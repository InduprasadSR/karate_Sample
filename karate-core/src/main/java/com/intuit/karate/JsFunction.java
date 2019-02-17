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

import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.exception.KarateException;
import java.util.HashMap;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 *
 * @author pthomas3
 */
public class JsFunction {

    public final String source;
    public final Value value;
    public final Context context;

    public JsFunction(Value value, Context context) {
        this("(" + value.toString() + ")", value, context);
    }

    public JsFunction(String source, Value value, Context context) {
        this.source = source;
        this.value = value;
        this.context = context;
    }

    public JsFunction copy(ScenarioContext sc) {
        Context newContext = JsUtils.createContext();
        Value bindings = newContext.getBindings("js");
        bindings.putMember(ScriptBindings.KARATE, sc.bridge);
        bindings.putMember(ScriptBindings.READ, sc.read);
        Map<String, JsFunction> functions = new HashMap();
        sc.vars.forEach((k, v) -> {
            if (v.isFunction()) {
                functions.put(k, v.getValue(JsFunction.class));
            } else {
                bindings.putMember(k, v.getAsJsValue());
            }
        });
        functions.forEach((k, v) -> {
            Value funValue = newContext.eval("js", v.source);
            bindings.putMember(k, funValue);
        });
        Value newValue = newContext.eval("js", source);
        return new JsFunction(source, newValue, newContext);
    }

    public ScriptValue invoke(Object arg, ScenarioContext ctx) {
        Value result;
        try {
            synchronized (context) {
                if (arg != null) {
                    result = value.execute(JsUtils.toJsValue(arg));
                } else {
                    result = value.execute();
                }
                Object object = JsUtils.fromJsValue(result, context);
                return new ScriptValue(object);
            }
        } catch (Exception e) {
            String message = "javascript function call failed: " + e.getMessage();
            if (ctx != null) {
                ctx.logger.error(message);
                ctx.logger.error("failed function body: " + value);
            }
            throw new KarateException(message);
        }
    }

}
