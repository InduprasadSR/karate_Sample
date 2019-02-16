package com.intuit.karate;

import com.intuit.karate.core.FeatureContext;
import com.intuit.karate.core.ScenarioContext;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ScriptBindingsTest {
    
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ScriptBindingsTest.class);
    
    private ScenarioContext getContext() {
        Path featureDir = FileUtils.getPathContaining(getClass());
        FeatureContext featureContext = FeatureContext.forWorkingDir("dev", featureDir.toFile());
        CallContext callContext = new CallContext(null, true);
        return new ScenarioContext(featureContext, callContext, null, null);
    }    
    
    @Test
    public void testBindings() {
        ScenarioContext ctx = getContext();
        String test = ctx.vars.get("someConfig", String.class);
        assertEquals("someValue", test);
        ScriptValue sv = ScriptBindings.eval("['a', 'b', 'c']", ctx.jsContext);
        assertTrue(sv.isListLike());    
        sv = ScriptBindings.eval("function(){ return ['a', 'b', 'c'] }", ctx.jsContext);
        assertTrue(sv.isFunction());
        ScriptValue result = sv.invokeFunction(ctx, null);
        assertTrue(result.isListLike());
        assertEquals("[\"a\",\"b\",\"c\"]", result.getAsString());
        JsFunction old = sv.getValue(JsFunction.class);
        JsFunction copy = old.copy(ctx);
        result = copy.invoke(null, ctx);
        assertTrue(result.isListLike());
        assertEquals("[\"a\",\"b\",\"c\"]", result.getAsString());        
    }
    
}
