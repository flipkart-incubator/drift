package com.flipkart.drift.worker.stringResolver;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

public class MustacheStringResolver implements StringResolver {
    MustacheFactory mf = new DefaultMustacheFactory();

    @Override
    public String resolve(String template,
                          Map<String, Object> context) {
        Mustache mustache = mf.compile(new StringReader(template), "example");
        StringWriter writer = new StringWriter();
        mustache.execute(writer, context);
        return writer.toString();
    }
}
