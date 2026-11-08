package cn.colortemperature.meter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.NodeList;

/** Check translation completeness and format safety using the actual shipped resources. */
public final class ResourceTest {
    private static final Pattern FORMAT = Pattern.compile("%(\\d+)\\$([.\\d]*)([sdf])");
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");
    private static int checks;
    private static void check(boolean pass, String message) {
        checks++;
        if (!pass) throw new AssertionError(message);
    }
    private static Map<String, String> load(String folder) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        NodeList list = factory.newDocumentBuilder().parse("app/src/main/res/" + folder + "/strings.xml").getElementsByTagName("string");
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < list.getLength(); i++) {
            String key = list.item(i).getAttributes().getNamedItem("name").getNodeValue();
            String value = list.item(i).getTextContent();
            if (value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
            value = value.replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\");
            check(!result.containsKey(key), "Duplicate resource " + key + " in " + folder);
            check(!value.trim().isEmpty(), "Empty resource " + key);
            result.put(key, value);
        }
        return result;
    }
    private static Map<Integer, Character> signature(String value) {
        Map<Integer, Character> result = new LinkedHashMap<>();
        Matcher matcher = FORMAT.matcher(value);
        while (matcher.find()) result.put(Integer.parseInt(matcher.group(1)), matcher.group(3).charAt(0));
        return result;
    }
    private static void format(String key, String value) {
        Map<Integer, Character> signature = signature(value);
        int count = 0;
        for (int index : signature.keySet()) count = Math.max(count, index);
        Object[] args = new Object[count];
        for (Map.Entry<Integer, Character> field : signature.entrySet()) {
            if (field.getValue() == 'f') args[field.getKey() - 1] = 123.5;
            else if (field.getValue() == 'd') args[field.getKey() - 1] = 3;
            else args[field.getKey() - 1] = "example";
        }
        check(!String.format(Locale.ENGLISH, value, args).isEmpty(), "Format failed for " + key);
    }
    public static void main(String[] args) throws Exception {
        Map<String, String> zh = load("values"), en = load("values-en");
        check(zh.keySet().equals(en.keySet()), "Chinese and English keys differ");
        for (String key : zh.keySet()) {
            check(signature(zh.get(key)).equals(signature(en.get(key))), "Format parameters differ for " + key);
            check(!CJK.matcher(en.get(key)).find(), "Untranslated English resource " + key);
            format(key, zh.get(key)); format(key, en.get(key));
        }
        for (String file : new String[]{"MainActivity.java", "SensorEngine.java", "MeterViews.java", "IconDock.java"}) {
            String source = new String(Files.readAllBytes(Paths.get("app/src/main/java/cn/colortemperature/meter/" + file)), StandardCharsets.UTF_8);
            Matcher literal = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"").matcher(source);
            while (literal.find()) {
                String text = literal.group();
                check(!CJK.matcher(text).find() || text.equals("\"中文\"") || text.equals("\"简体中文\""), "Hardcoded UI text in " + file + ": " + text);
            }
        }
        for (String[] policy : new String[][]{{"PRIVACY.md", "privacy_body"}, {"TERMS.md", "terms_body"}}) {
            String text = new String(Files.readAllBytes(Paths.get(policy[0])), StandardCharsets.UTF_8).replace("\r\n", "\n");
            check(text.contains(zh.get(policy[1])), "Chinese policy document differs from app");
            check(text.contains(en.get(policy[1])), "English policy document differs from app");
            check(zh.get(policy[1]).contains("PATRICK STAR") && en.get(policy[1]).contains("PATRICK STAR"), "Missing credit in policy");
        }
        String manifest = new String(Files.readAllBytes(Paths.get("app/src/main/AndroidManifest.xml")), StandardCharsets.UTF_8);
        check(!manifest.contains("uses-permission"), "Privacy policy must be reviewed when permissions change");
        check(manifest.contains("android:allowBackup=\"false\""), "Local-only policy must be reviewed when backups change");
        System.out.println("Passed " + checks + " translation, formatting, and policy consistency checks (" + zh.size() + " keys per language).");
    }
}
