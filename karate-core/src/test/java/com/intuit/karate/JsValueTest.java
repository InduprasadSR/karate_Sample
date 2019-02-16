package com.intuit.karate;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class JsValueTest {
    
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(JsValueTest.class);
    
    @Test
    public void testGraal() {
        Context c = Context.newBuilder("js").allowAllAccess(true).build();
        Value v = c.eval("js", "(function(){ return ['a', 'b', 'c'] })");
        assertTrue(v.canExecute());
        Value res = v.execute();
        assertTrue(res.hasArrayElements());
        String json = JsUtils.toJson(res, c);
        assertEquals("[\"a\",\"b\",\"c\"]", json);
        String body = v.toString();
        assertEquals("function(){ return ['a', 'b', 'c'] }", body);        
        v = c.eval("js", "Java.type('com.intuit.karate.SimplePojo')");
        assertTrue(v.canInstantiate());        
        c.getBindings("js").putMember("SimplePojo", v);
        Value sp = c.eval("js", "new SimplePojo()");
        assertTrue(sp.isHostObject());
        SimplePojo o = sp.as(SimplePojo.class);
        assertEquals(null, o.getFoo());
        assertEquals(0, o.getBar());
    }
    
}
