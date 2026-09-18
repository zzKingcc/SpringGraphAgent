package com.zzkingcc.stringer.server.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.zzkingcc.stringer.api.tool.ToolDescriptor;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 把工具实例声明的 <b>JSON Schema</b> 翻译成内核能用的两样东西
 * @author zzkingcc
 */
final class ToolParamSchema {

    private ToolParamSchema() {
    }

    /** 由根级 JSON Schema 生成喂给 LLM 的参数规格（递归保留嵌套结构） */
    static JsonObjectSchema toSpecificationSchema(JsonNode parameters) {
        if (parameters == null || !parameters.isObject()) {
            return JsonObjectSchema.builder().build();
        }
        return objectSchema(parameters);
    }

    /** 由根级 JSON Schema 派生描述符参数列表（只看顶层，嵌套结构退化为 object / array） */
    static List<ToolDescriptor.Param> toParams(JsonNode parameters) {
        List<ToolDescriptor.Param> params = new ArrayList<>();
        if (parameters == null || !parameters.isObject()) {
            return params;
        }
        JsonNode properties = parameters.path("properties");
        if (!properties.isObject()) {
            return params;
        }

        List<String> required = textList(parameters.path("required"));
        Iterator<Map.Entry<String, JsonNode>> it = properties.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String name = entry.getKey();
            JsonNode property = entry.getValue();
            List<String> allowValues = textList(property.path("enum"));
            String type = allowValues.isEmpty() ? typeName(property) : "enum";
            params.add(new ToolDescriptor.Param(
                    name,
                    type,
                    property.path("description").asText("（未描述）"),
                    required.contains(name),
                    List.copyOf(allowValues),
                    property.path("example").asText(""),
                    property.path("x-sensitive").asBoolean(false)));
        }
        return params;
    }

    private static JsonObjectSchema objectSchema(JsonNode node) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        JsonNode properties = node.path("properties");
        if (properties.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = properties.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                builder.addProperty(entry.getKey(), element(entry.getValue()));
            }
        }
        List<String> required = textList(node.path("required"));
        if (!required.isEmpty()) {
            builder.required(required.toArray(new String[0]));
        }
        return builder.build();
    }

    private static JsonSchemaElement element(JsonNode node) {
        if (node == null || !node.isObject()) {
            return JsonStringSchema.builder().build();
        }
        String description = node.path("description").asText("");

        // enum 优先于 type：JSON Schema 里枚举通常是 {"type":"string","enum":[...]}
        List<String> allowValues = textList(node.path("enum"));
        if (!allowValues.isEmpty()) {
            return JsonEnumSchema.builder().description(description).enumValues(allowValues).build();
        }

        return switch (typeName(node)) {
            case "object" -> objectSchema(node);
            case "array" -> JsonArraySchema.builder()
                    .description(description)
                    .items(element(node.path("items")))
                    .build();
            case "integer" -> JsonIntegerSchema.builder().description(description).build();
            case "number" -> JsonNumberSchema.builder().description(description).build();
            case "boolean" -> JsonBooleanSchema.builder().description(description).build();
            default -> JsonStringSchema.builder().description(description).build();
        };
    }

    /** JSON Schema 的 type 名（缺省按 string 处理，与本地工具对未知类型的兜底一致） */
    private static String typeName(JsonNode node) {
        JsonNode type = node.path("type");
        String name = type.isArray() ? type.path(0).asText("") : type.asText("");
        return name == null || name.isBlank() ? "string" : name;
    }

    private static List<String> textList(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode node : array) {
                String text = node.asText("");
                if (!text.isBlank()) {
                    values.add(text);
                }
            }
        }
        return values;
    }
}
