/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.core;

import com.intuit.karate.AssertionResult;
import com.intuit.karate.FileUtils;
import com.intuit.karate.JsFunction;
import com.intuit.karate.JsUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.PerfContext;
import com.intuit.karate.Script;
import com.intuit.karate.ScriptBindings;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.ScriptValueMap;
import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.exception.KarateAbortException;
import com.intuit.karate.http.HttpRequestBuilder;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.HttpUtils;
import com.intuit.karate.http.MultiValuedMap;
import com.intuit.karate.netty.WebSocketClient;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.polyglot.Value;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class ScriptBridge implements PerfContext {

    private static final Object GLOBALS_LOCK = new Object();
    private static final Map<String, Object> GLOBALS = new HashMap();

    public final ScenarioContext context;

    public ScriptBridge(ScenarioContext context) {
        this.context = context;
    }

    public ScenarioContext getContext() {
        return context;
    }

    public void configure(String key, Value v) {
        context.configure(key, new ScriptValue(fromJs(v)));
    }
    
    public void configure(String key, Object o) { // for extreme java in js config attempts
        configure(key, Value.asValue(o));
    }    

    public Object read(String fileName) {
        return context.read.apply(fileName);
    }

    public String readAsString(String fileName) {
        return FileUtils.readFileAsString(fileName, context);
    }

    public String pretty(Value v) {
        ScriptValue sv = new ScriptValue(fromJs(v));
        return sv.getAsPrettyString();
    }

    public String prettyXml(Value v) {
        ScriptValue sv = new ScriptValue(fromJs(v));
        if (sv.isXml()) {
            Node node = sv.getValue(Node.class);
            return XmlUtils.toString(node, true);
        } else if (sv.isMapLike()) {
            Document doc = XmlUtils.fromMap(sv.getAsMap());
            return XmlUtils.toString(doc, true);
        } else {
            String xml = sv.getAsString();
            Document doc = XmlUtils.toXmlDoc(xml);
            return XmlUtils.toString(doc, true);
        }
    }

    private Object fromJs(Value v) {
        return JsUtils.fromJsValue(v, context.jsContext);
    }

    public void set(String name, Value v) {
        context.vars.put(name, fromJs(v));
    }

    public void setXml(String name, String xml) {
        context.vars.put(name, XmlUtils.toXmlDoc(xml));
    }

    // this makes sense mainly for xpath manipulation from within js
    public void set(String name, String path, Object o) {
        Script.setValueByPath(name, path, new ScriptValue(o), context);
    }

    // this makes sense for xml / xpath manipulation from within js
    public void remove(String name, String path) {
        Script.removeValueByPath(name, path, context);
    }

    public Object get(String exp) {
        ScriptValue sv;
        try {
            sv = Script.evalKarateExpression(exp, context); // even json path expressions will work
        } catch (Exception e) {
            context.logger.trace("karate.get failed for expression: '{}': {}", exp, e.getMessage());
            return null;
        }
        if (sv != null) {
            return sv.getAsJsValue();
        } else {
            return null;
        }
    }

    public void add(String name, Value v) {
        ScriptValue sv = context.vars.get(name);
        if (sv != null && sv.isListLike()) {
            List list = sv.getAsList();
            list.add(fromJs(v));
            context.vars.put(name, list);
        }
    }
    
    public long sizeOf(Value v) {
        if (v.hasArrayElements()) {
            return v.getArraySize();
        } else if (v.hasMembers()) {
            return v.getMemberKeys().size();
        } else {
            return -1;
        }
    }
    
    public Object keysOf(Value v) {
        if (v.hasMembers()) {
            return new ArrayList(v.getMemberKeys());
        } else {
            return Collections.EMPTY_LIST;
        }
    }
    
    public Object valuesOf(Value v) {
        if (v.hasMembers()) {
            Set<String> keys = v.getMemberKeys();
            List list = new ArrayList(keys.size());
            for (String key : keys) {
                Value temp = v.getMember(key);
                list.add(fromJs(temp));
            }
            return JsUtils.toJsValue(list);
        } else {
            return Collections.EMPTY_LIST;
        }
    }    

    public Object match(Object actual, Object expected) {
        AssertionResult result = Script.matchNestedObject('.', "$", MatchType.EQUALS, actual, null, actual, expected, context);
        Map<String, Object> map = new HashMap(2);
        map.put("pass", result.pass);
        map.put("message", result.message);
        return JsUtils.toJsValue(map);
    }

    public void forEach(Map<String, Object> map, Value fun) {
        if (map == null) {
            return;
        }
        if (!fun.canExecute()) {
            throw new RuntimeException("not a JS function: " + fun);
        }
        AtomicInteger i = new AtomicInteger();
        map.forEach((k, v) -> fun.execute(k, v, i.getAndIncrement()));
    }

    public void forEach(List list, Value fun) {
        if (list == null) {
            return;
        }
        if (!fun.canExecute()) {
            throw new RuntimeException("not a JS function: " + fun);
        }
        for (int i = 0; i < list.size(); i++) {
            fun.execute(list.get(i), i);
        }
    }

    public Object map(List list, Value fun) {
        if (list == null) {
            return new ArrayList();
        }
        if (!fun.canExecute()) {
            throw new RuntimeException("not a JS function: " + fun);
        }
        List res = new ArrayList(list.size());
        for (int i = 0; i < list.size(); i++) {
            Value v = fun.execute(list.get(i), i);
            res.add(fromJs(v));
        }
        return JsUtils.toJsValue(res);
    }

    public Object filter(List list, Value fun) {
        if (list == null) {
            return new ArrayList();
        }
        if (!fun.canExecute()) {
            throw new RuntimeException("not a JS function: " + fun);
        }
        List res = new ArrayList();
        for (int i = 0; i < list.size(); i++) {
            Object x = list.get(i);
            Value v = fun.execute(x, i);
            if (v.isBoolean() && v.asBoolean()) {
                res.add(x);
            } else if (v.isNumber()) { // support truthy numbers as a convenience
                Number num = v.as(Number.class);
                if (num.intValue() != 0) {
                    res.add(x);
                }
            }
        }
        return JsUtils.toJsValue(res);
    }

    public Object jsonPath(Object o, String exp) {
        DocumentContext doc;
        if (o instanceof DocumentContext) {
            doc = (DocumentContext) o;
        } else {
            doc = JsonPath.parse(o);
        }
        Object result = doc.read(exp);
        return JsUtils.toJsValue(result);
    }

    public Object lowerCase(Object o) {
        ScriptValue sv = new ScriptValue(o);
        return JsUtils.toJsValue(sv.toLowerCase());
    }

    public Object xmlPath(Object o, String path) {
        if (!(o instanceof Node)) {
            if (o instanceof Map) {
                o = XmlUtils.fromMap((Map) o);
            } else {
                throw new RuntimeException("not XML or cannot convert: " + o);
            }
        }
        Node result = XmlUtils.getNodeByPath((Node) o, path, false);
        int childElementCount = XmlUtils.getChildElementCount(result);
        if (childElementCount == 0) {
            return StringUtils.trimToNull(result.getTextContent());
        }
        return XmlUtils.toNewDocument(result);
    }

    public Object toBean(Object o, String className) {
        ScriptValue sv = new ScriptValue(o);
        return JsonUtils.fromJson(sv.getAsString(), className);
    }

    public Object call(String fileName) {
        return call(fileName, null);
    }

    public Object call(String fileName, Object arg) {
        ScriptValue sv = FileUtils.readFile(fileName, context);
        switch (sv.getType()) {
            case FEATURE:
                Feature feature = sv.getValue(Feature.class);
                Object fo = Script.callFeature(feature, arg, context, false).getValue();
                return JsUtils.toJsValue(fo); // TODO as script-value method
            case FUNCTION:
                JsFunction function = sv.getValue(JsFunction.class);
                Object jo = function.invoke(arg, context).getValue();
                return JsUtils.toJsValue(jo);
            default:
                context.logger.warn("not a js function or feature file: {} - {}", fileName, sv);
                return null;
        }
    }

    public Object callSingle(String fileName) {
        return callSingle(fileName, null);
    }

    public Object callSingle(String fileName, Object arg) {
        if (GLOBALS.containsKey(fileName)) {
            context.logger.trace("callSingle cache hit: {}", fileName);
            return GLOBALS.get(fileName);
        }
        long startTime = System.currentTimeMillis();
        context.logger.trace("callSingle waiting for lock: {}", fileName);
        synchronized (GLOBALS_LOCK) { // lock
            if (GLOBALS.containsKey(fileName)) { // retry
                long endTime = System.currentTimeMillis() - startTime;
                context.logger.warn("this thread waited {} milliseconds for callSingle lock: {}", endTime, fileName);
                return GLOBALS.get(fileName);
            }
            // this thread is the 'winner'
            context.logger.info(">> lock acquired, begin callSingle: {}", fileName);
            Object jsValue = call(fileName, arg);
            GLOBALS.put(fileName, jsValue);
            context.logger.info("<< lock released, cached callSingle: {}", fileName);
            return jsValue;
        }
    }

    public Object getPrevRequest() { // TODO util to this
        Map map = context.getPrevRequest().toMap();
        return JsUtils.toJsValue(map);
    }

    public Object eval(String exp) {
        ScriptValue sv = Script.evalJsExpression(exp, context);
        return sv.getValue();
    }

    public List<String> getTags() {
        return context.tags;
    }

    public Map<String, List<String>> getTagValues() {
        return context.tagValues;
    }

    public Object getInfo() { // will be JSON / map-like
        DocumentContext doc = JsonUtils.toJsonDoc(context.scenarioInfo);
        return JsUtils.toJsValue(doc.read("$"));
    }

    public void proceed() {
        proceed(null);
    }

    public void proceed(String requestUrlBase) {
        HttpRequestBuilder request = new HttpRequestBuilder();
        String urlBase = requestUrlBase == null ? getAsString(ScriptValueMap.VAR_REQUEST_URL_BASE) : requestUrlBase;
        String uri = getAsString(ScriptValueMap.VAR_REQUEST_URI);
        String url = uri == null ? urlBase : urlBase + uri;
        request.setUrl(url);
        request.setMethod(getAsString(ScriptValueMap.VAR_REQUEST_METHOD));
        request.setHeaders(getValue(ScriptValueMap.VAR_REQUEST_HEADERS).getValue(MultiValuedMap.class));
        request.removeHeaderIgnoreCase(HttpUtils.HEADER_CONTENT_LENGTH);
        request.setBody(getValue(ScriptValueMap.VAR_REQUEST));
        HttpResponse response = context.getHttpClient().invoke(request, context);
        context.setPrevResponse(response);
        context.updateResponseVars();
    }

    public void abort() {
        throw new KarateAbortException("[karate:abort]");
    }

    public void embed(Object o, String contentType) {
        ScriptValue sv = new ScriptValue(o);
        if (contentType == null) {
            contentType = HttpUtils.getContentType(sv);
        }
        Embed embed = new Embed();
        embed.setBytes(sv.getAsByteArray());
        embed.setMimeType(contentType);
        context.prevEmbed = embed;
    }

    public void write(Object o, String path) {
        ScriptValue sv = new ScriptValue(o);
        path = Engine.getBuildDir() + File.separator + path;
        FileUtils.writeToFile(new File(path), sv.getAsByteArray());
    }

    public WebSocketClient webSocket(String url, Value textHandlerValue) {
        return context.webSocket(url, null, textHandlerValue, null);
    }

    public WebSocketClient webSocket(String url, String subProtocol, Value textHandlerValue) {
        return context.webSocket(url, subProtocol, textHandlerValue, null);
    }

    public WebSocketClient webSocket(String url, String subProtocol, Value textHandlerValue, Value binaryHandlerValue) {
        return context.webSocket(url, subProtocol, textHandlerValue, binaryHandlerValue);
    }

    public void signal(Value value) {
        context.signal(fromJs(value));
    }

    public Object listen(long timeout, Value value) {
        if (!value.canExecute()) {
            throw new RuntimeException("not a JS function: " + value);
        }
        Object result = context.listen(timeout, value);
        return JsUtils.toJsValue(result);
    }

    public Object listen(long timeout) {
        return context.listen(timeout, null);
    }

    private ScriptValue getValue(String name) {
        ScriptValue sv = context.vars.get(name);
        return sv == null ? ScriptValue.NULL : sv;
    }

    private String getAsString(String name) {
        return getValue(name).getAsString();
    }

    public boolean pathMatches(String path) {
        String uri = getAsString(ScriptValueMap.VAR_REQUEST_URI);
        Map<String, String> map = HttpUtils.parseUriPattern(path, uri);
        context.vars.put(ScriptBindings.PATH_PARAMS, map);
        return map != null;
    }

    public boolean methodIs(String method) {
        String actual = getAsString(ScriptValueMap.VAR_REQUEST_METHOD);
        return actual.equalsIgnoreCase(method);
    }

    public Object paramValue(String name) {
        Map<String, List<String>> params = (Map) getValue(ScriptValueMap.VAR_REQUEST_PARAMS).getValue();
        if (params == null) {
            return null;
        }
        List<String> list = params.get(name);
        if (list == null) {
            return null;
        }
        if (list.size() == 1) {
            return list.get(0);
        }
        return list;
    }

    public boolean headerContains(String name, String test) {
        Map<String, List<String>> headers = (Map) getValue(ScriptValueMap.VAR_REQUEST_HEADERS).getValue();
        if (headers == null) {
            return false;
        }
        List<String> list = headers.get(name);
        if (list == null) {
            return false;
        }
        for (String s : list) {
            if (s != null && s.contains(test)) {
                return true;
            }
        }
        return false;
    }

    public boolean typeContains(String test) {
        return headerContains(HttpUtils.HEADER_CONTENT_TYPE, test);
    }

    public boolean acceptContains(String test) {
        return headerContains(HttpUtils.HEADER_ACCEPT, test);
    }

    public Object bodyPath(String path) {
        ScriptValue sv = context.vars.get(ScriptValueMap.VAR_REQUEST);
        if (sv == null || sv.isNull()) {
            return null;
        }
        if (path.startsWith("/")) {
            return xmlPath(sv.getValue(), path);
        } else {
            return jsonPath(sv.getValue(), path);
        }
    }

    public String getEnv() {
        return context.featureContext.env;
    }

    public Object getProperties() {
        return JsUtils.toJsValue(System.getProperties());
    }

    public void setLocation(String expression) {
        context.driver(expression);
    }

    public void log(Object... objects) {
        if (context.isPrintEnabled()) {
            context.logger.info("{}", new LogWrapper(objects));
        }
    }
    
    public JsFunction toJava(Value value) {
        return new JsFunction(value, context.jsContext).copy(context);
    }
    
    @Override
    public void capturePerfEvent(String name, long startTime, long endTime) {
        PerfEvent event = new PerfEvent(startTime, endTime, name, 200);
        context.capturePerfEvent(event);
    }

    // make sure toString() is lazy
    static class LogWrapper {

        private final Object[] objects;

        LogWrapper(Object... objects) {
            this.objects = objects;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Object o : objects) {
                sb.append(o).append(' ');
            }
            return sb.toString();
        }

    }

}
