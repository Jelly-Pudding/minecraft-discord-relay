package com.jellypudding.discordRelay.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WordFilterUtil {

    private final Map<Pattern, String> compiledPatterns;
    private final boolean enabled;

    public WordFilterUtil(boolean enabled, List<String> filterList) {
        this.enabled = enabled;
        this.compiledPatterns = new HashMap<>();

        if (filterList != null) {
            for (String entry : filterList) {
                String[] parts = entry.split(":", 2);
                if (parts.length == 2) {
                    String badWord = parts[0].trim().toLowerCase();
                    String replacement = parts[1].trim();

                    Pattern pattern = Pattern.compile("\\b" + Pattern.quote(badWord) + "(s|ed|ing)?\\b", 
                            Pattern.CASE_INSENSITIVE);
                    compiledPatterns.put(pattern, replacement);
                }
            }
        }
    }

    public String filterMessage(String message) {
        if (!enabled || message == null || message.isEmpty()) {
            return message;
        }

        String filtered = message;

        for (Map.Entry<Pattern, String> entry : compiledPatterns.entrySet()) {
            Pattern pattern = entry.getKey();
            String replacement = entry.getValue();

            Matcher matcher = pattern.matcher(filtered);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String matched = matcher.group();
                String suffix = matcher.group(1);
                String replacementWord = getReplacementWithSuffix(replacement, suffix, matched);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacementWord));
            }
            matcher.appendTail(sb);
            filtered = sb.toString();
        }

        return filtered;
    }

    private String getReplacementWithSuffix(String replacement, String suffix, String originalMatch) {
        if (suffix == null || suffix.isEmpty()) {
            return matchCase(replacement, originalMatch);
        }

        String result;
        switch (suffix.toLowerCase()) {
            case "s":
                result = replacement + "s";
                break;
            case "ed":
                if (replacement.endsWith("y")) {
                    result = replacement.substring(0, replacement.length() - 1) + "ied";
                } else if (replacement.endsWith("e")) {
                    result = replacement + "d";
                } else {
                    result = replacement + "ed";
                }
                break;
            case "ing":
                if (replacement.endsWith("e") && replacement.length() > 1) {
                    result = replacement.substring(0, replacement.length() - 1) + "ing";
                } else {
                    result = replacement + "ing";
                }
                break;
            default:
                result = replacement + suffix;
        }

        return matchCase(result, originalMatch);
    }

    private String matchCase(String replacement, String original) {
        if (original.equals(original.toUpperCase())) {
            return replacement.toUpperCase();
        } else if (Character.isUpperCase(original.charAt(0))) {
            return Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
        }
        return replacement;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

