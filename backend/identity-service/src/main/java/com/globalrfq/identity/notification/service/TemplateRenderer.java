package com.globalrfq.identity.notification.service;

import com.globalrfq.identity.notification.model.NotificationTemplate;
import com.globalrfq.identity.notification.model.RenderedMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    public RenderedMessage render(NotificationTemplate template, Map<String, String> variables) {
        String subject = replace(template.getSubject(), variables, v -> v.replaceAll("[\\r\\n]", " "));
        String html = replace(template.getHtmlBody(), variables, HtmlUtils::htmlEscape);
        String text = replace(template.getTextBody(), variables, v -> v);
        return new RenderedMessage(subject, html, text);
    }

    private String replace(String source, Map<String, String> variables, UnaryOperator<String> encoder) {
        Matcher matcher = PLACEHOLDER.matcher(source);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.get(key);
            if (value == null) {
                throw new IllegalArgumentException("Missing template variable: " + key);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(encoder.apply(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}