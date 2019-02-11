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

import java.util.List;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;

/**
 *
 * @author pthomas3
 */
public class JsValue {

    public final Context context;
    public final Value value;

    public JsValue(Value value, Context context) {
        this.value = value;
        this.context = context;
    }
    
    public static String toJson(Value v, Context c) {
        Value stringify = c.eval("js", "JSON.stringify");
        Value json = stringify.execute(v);
        return json.asString();        
    }

    public String toJson() {
        return toJson(value, context);
    }

    public static Object toJsValue(Object o) {
        if (o instanceof JsValue) { // FUNCTION
            return ((JsValue) o).value;
        } else if (o instanceof List) {
            return new JsList((List) o);
        } else if (o instanceof Map) {
            return new JsMap((Map) o);
        } else {
            return o;
        }
    }
    
    public static Object fromJsValue(Value v, Context c) {
        if (v.canExecute()) {
            return new JsValue(v, c);
        } else if (v.isHostObject()) {
            return v.asHostObject();
        } else if (v.isProxyObject()) {
            Proxy proxy = v.asProxyObject();
            if (proxy instanceof JsList) {
                return ((JsList) proxy).getValue();
            } else if (proxy instanceof JsMap) {
                return ((JsMap) proxy).getValue();
            } else {
                throw new RuntimeException("unexpected proxy: " + proxy);
            }
        } else if (v.isString()) {
            return v.asString();
        } else if (v.isNumber()) {
            return v.as(Number.class);
        } else if (v.isBoolean()) {
            return v.asBoolean();
        } else if (v.isNull()) {
            return null;
        } else if (v.hasMembers()) { // JS object or array
            String json = toJson(v, c);
            return JsonUtils.toJsonDoc(json);
        } else {
            throw new RuntimeException("unable to unpack: " + v);
        }
    }

}
