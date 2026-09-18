package com.zzkingcc.stringer.runtime.tool;

import com.zzkingcc.stringer.api.annotation.StringerTool;
import com.zzkingcc.stringer.api.annotation.ToolParam;
import com.zzkingcc.stringer.api.annotation.ToolPolicy;
import com.zzkingcc.stringer.api.tool.ToolDescriptor;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 注解扫描器
 * @author zzkingcc
 */
public final class AnnotatedToolScanner {

    private static final Logger log = LoggerFactory.getLogger(AnnotatedToolScanner.class);

    private AnnotatedToolScanner() {
    }

    /**
     * 扫描一个工具提供者实例
     *
     * @param provider 实现 {@code StringerToolProvider} 的 Bean
     * @return 该 Bean 上所有工具方法的注册项（可能为空）
     */
    public static List<ToolRegistry.Registered> scan(Object provider) {
        List<ToolRegistry.Registered> registered = new ArrayList<>();
        if (provider == null) {
            return registered;
        }

        for (Method method : provider.getClass().getMethods()) {
            StringerTool annotation = method.getAnnotation(StringerTool.class);
            if (annotation == null) {
                continue;
            }
            registered.add(toRegistered(provider, method, annotation));
        }

        if (registered.isEmpty()) {
            log.warn("[工具扫描] {} 未实现任何 @StringerTool 方法，已注册 0 个工具",
                    provider.getClass().getName());
        }
        return registered;
    }

    private static ToolRegistry.Registered toRegistered(Object provider, Method method, StringerTool annotation) {
        String name = annotation.name().isBlank() ? method.getName() : annotation.name();
        String source = provider.getClass().getSimpleName() + "#" + method.getName();

        List<ToolDescriptor.Param> params = resolveParams(method);
        ToolDescriptor.Approval approval = resolveApproval(method);
        List<String> profiles = resolveProfiles(annotation);

        ToolDescriptor descriptor = new ToolDescriptor(
                name,
                annotation.description(),
                annotation.category(),
                annotation.version(),
                annotation.sideEffect(),
                annotation.idempotent(),
                annotation.toModel(),
                List.copyOf(params),
                List.copyOf(profiles),
                approval,
                source);

        ToolSpecification specification = ToolSpecification.builder()
                .name(name)
                .description(annotation.description())
                .parameters(toJsonSchema(params))
                .build();

        // 复用 LangChain4j 的参数反序列化：arguments(JSON) → 方法参数
        DefaultToolExecutor executor = new DefaultToolExecutor(provider, method);

        if (descriptor.requiresApproval()
                && "CONDITIONAL".equalsIgnoreCase(approval.mode())
                && approval.condition().isBlank()) {
            log.warn("[工具扫描] {} 声明 CONDITIONAL 审批但未写 condition，将按 ALWAYS 处理（保守）", name);
        }

        return ToolRegistry.Registered.local(descriptor, specification, executor);
    }

    /**
     * 反射得结构
     */
    private static List<ToolDescriptor.Param> resolveParams(Method method) {
        List<ToolDescriptor.Param> params = new ArrayList<>();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);

            String name = toolParam != null && !toolParam.name().isBlank()
                    ? toolParam.name()
                    : fallbackParamName(parameter, i);

            Class<?> rawType = parameter.getType();
            List<String> allowValues = toolParam != null && toolParam.allowValues().length > 0
                    ? Arrays.asList(toolParam.allowValues())
                    : enumValues(rawType);

            String type = jsonType(rawType);
            if (!allowValues.isEmpty()) {
                type = "enum";
            }

            String description = toolParam != null ? toolParam.description() : "（未描述）";
            boolean required = toolParam == null || toolParam.required();
            if (rawType == Optional.class) {
                required = false;
            }
            String example = toolParam != null ? toolParam.example() : "";
            boolean sensitive = toolParam != null && toolParam.sensitive();

            params.add(new ToolDescriptor.Param(name, type, description, required,
                    allowValues, example, sensitive));
        }
        return params;
    }

    private static String fallbackParamName(Parameter parameter, int index) {
        String reflected = parameter.getName();
        // 未开启 -parameters 编译参数时形参名会是 arg0 / arg1
        if (reflected == null || reflected.isBlank() || reflected.startsWith("arg")) {
            return "param" + (index + 1);
        }
        return reflected;
    }

    private static List<String> enumValues(Class<?> type) {
        if (type.isEnum()) {
            return Arrays.stream(type.getEnumConstants()).map(Object::toString).toList();
        }
        return List.of();
    }

    /**
     * Java 类型 → JSON Schema 类型名（只输出字符串，不保留 Class 引用）
     */
    private static String jsonType(Class<?> type) {
        if (type == String.class || type == CharSequence.class || type == Character.class || type == char.class) {
            return "string";
        }
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class
                || type == short.class || type == Short.class || type == byte.class || type == Byte.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class || type == float.class || type == Float.class
                || type == BigDecimal.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type.isEnum()) {
            return "enum";
        }
        if (type.isArray() || Collection.class.isAssignableFrom(type)) {
            return "array";
        }
        // 复杂对象（DTO / record）退化为 string 描述
        return "string";
    }

    /**
     * 由参数描述生成 JSON Schema
     */
    private static JsonObjectSchema toJsonSchema(List<ToolDescriptor.Param> params) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        List<String> required = new ArrayList<>();

        for (ToolDescriptor.Param param : params) {
            builder.addProperty(param.name(), toSchemaElement(param));
            if (param.required()) {
                required.add(param.name());
            }
        }

        if (!required.isEmpty()) {
            builder.required(required.toArray(new String[0]));
        }
        return builder.build();
    }

    private static JsonSchemaElement toSchemaElement(ToolDescriptor.Param param) {
        String description = param.description();
        return switch (param.type()) {
            case "integer" -> JsonIntegerSchema.builder().description(description).build();
            case "number" -> JsonNumberSchema.builder().description(description).build();
            case "boolean" -> JsonBooleanSchema.builder().description(description).build();
            case "enum" -> JsonEnumSchema.builder()
                    .description(description)
                    .enumValues(param.allowValues())
                    .build();
            case "array" -> JsonArraySchema.builder()
                    .description(description)
                    .items(JsonStringSchema.builder().build())
                    .build();
            default -> JsonStringSchema.builder().description(description).build();
        };
    }

    /**
     * 读取 {@code @ToolPolicy}
     */
    private static ToolDescriptor.Approval resolveApproval(Method method) {
        ToolPolicy policy = method.getAnnotation(ToolPolicy.class);
        if (policy == null) {
            return ToolDescriptor.Approval.none();
        }
        ToolPolicy.Approval approval = policy.approval();
        if (approval.mode() == ToolPolicy.Approval.Mode.NONE) {
            return ToolDescriptor.Approval.none();
        }
        return new ToolDescriptor.Approval(
                approval.mode().name(),
                approval.condition(),
                approval.reason(),
                Arrays.asList(approval.approverRoles()),
                approval.timeoutSeconds());
    }

    /**
     * 读取 {@code @StringerTool#profiles()} 作为本工具所属的域。
     */
    private static List<String> resolveProfiles(StringerTool annotation) {
        String[] raw = annotation.profiles();
        if (raw == null || raw.length == 0) {
            return List.of();
        }
        return Arrays.stream(raw)
                .filter(p -> p != null && !p.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
