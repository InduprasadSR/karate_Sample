/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
package com.intuit.karate;

import com.intuit.karate.core.Feature;
import com.jayway.jsonpath.DocumentContext;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class JsUtils {

    private static final Engine ENGINE = Engine.create();

    public static Context createContext() {
        return Context.newBuilder("js")
                .engine(ENGINE)
                .option("js.nashorn-compat", "true")
                .allowAllAccess(true).build();
    }

    public static String toJson(Value v, Context c) {
        Value stringify = c.eval("js", "JSON.stringify");
        Value json = stringify.execute(v);
        return json.asString();
    }

    public static Object toJsValue(Object o) {
        if (o instanceof List) {
            return new JsList((List) o);
        } else if (o instanceof Map) {
            return new JsMap((Map) o);
        } else if (o instanceof JsFunction) {
            return ((JsFunction) o).value;
        } else {
            return o;
        }
    }

    public static Object fromJsValue(Object object, Context c) {
        Value v = Value.asValue(object);
        if (v.isString()) {
            return v.asString();
        } else if (v.isNumber()) {
            return v.as(Number.class);
        } else if (v.isBoolean()) {
            return v.asBoolean();
        } else if (v.isNull()) {
            return null;
        } else if (v.canExecute()) {
            return new JsFunction(v, c);
        } else if (v.isHostObject()) {
            Object o = v.asHostObject();
            if (o instanceof JsMap) {
                return ((JsMap) o).getValue();
            } else if (o instanceof JsList) {
                return ((JsList) o).getValue();
            } else if (o instanceof Feature
                    || o instanceof DocumentContext
                    || o instanceof Node
                    // rare cases of nested call and js functions 
                    || o instanceof Map && !(o instanceof Properties) // relevant edge case
                    || o instanceof List
                    || o instanceof InputStream
                    || o instanceof byte[]
                    || o instanceof BigDecimal) {
                return o;
            } else {
                return v;
            }
        } else if (v.isProxyObject()) {
            Object o = v.asProxyObject();
            if (o instanceof JsMap) {
                return ((JsMap) o).getValue();
            } else if (o instanceof JsList) {
                return ((JsList) o).getValue();
            } else {
                throw new RuntimeException("unexpected proxy: " + o);
            }
        } else if (v.hasMembers()) { // object or array that originated from JS
            String json = toJson(v, c);
            return JsonUtils.toJsonDoc(json).read("$");
        } else {
            throw new RuntimeException("unable to unpack: " + v);
        }
    }

}
