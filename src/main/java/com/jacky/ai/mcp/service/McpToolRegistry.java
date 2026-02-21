package com.jacky.ai.mcp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacky.ai.mcp.config.McpProperties;
import com.jacky.ai.mcp.exception.McpProtocolException;
import com.jacky.ai.mcp.tool.McpToolHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MCP 工具注册中心（混合注册器）：
 * 1. 自动发现 Spring Bean 上的 @Tool 方法
 * 2. 读取 app.mcp.tools 做配置覆盖（标题、描述、schema、hint、启用状态）
 * 3. 保留 McpToolHandler 作为兜底实现（优先级低于 @Tool）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolRegistry {

    private static final String SOURCE_CONFIG_OVERRIDE = "CONFIG_OVERRIDE";
    private static final String SOURCE_AUTO_TOOL = "AUTO_TOOL";
    private static final String SOURCE_MANUAL_HANDLER = "MANUAL_HANDLER";

    private final McpProperties mcpProperties;
    private final ApplicationContext applicationContext;
    private final List<McpToolHandler> handlers;
    private final ObjectMapper objectMapper;

    private final Map<String, ResolvedTool> resolvedTools = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        if (!mcpProperties.isEnabled()) {
            return;
        }

        Map<String, DiscoveredTool> discoveredTools = discoverAnnotatedTools();
        Map<String, McpToolHandler> manualHandlers = collectManualHandlers();

        List<McpProperties.ToolDefinition> configuredTools = mcpProperties.getTools() == null
                ? List.of()
                : mcpProperties.getTools();

        LinkedHashSet<String> configuredNames = new LinkedHashSet<>();
        for (McpProperties.ToolDefinition definition : configuredTools) {
            if (!definition.isEnabled()) {
                continue;
            }
            if (!StringUtils.hasText(definition.getName())) {
                throw new IllegalStateException("MCP tool name must not be empty.");
            }
            String key = normalize(definition.getName());
            if (!configuredNames.add(key)) {
                throw new IllegalStateException("Duplicate MCP tool config: " + definition.getName());
            }

            ResolvedTool resolved = resolveFromConfig(definition, discoveredTools.get(key), manualHandlers.get(key));
            resolvedTools.put(key, resolved);
        }

        if (mcpProperties.isAutoRegisterUnconfiguredTools()) {
            autoRegisterDiscoveredTools(discoveredTools, configuredNames);
            autoRegisterManualHandlers(manualHandlers, configuredNames);
        }

        if (resolvedTools.isEmpty()) {
            throw new IllegalStateException(
                    "No MCP tools available. Please configure app.mcp.tools or expose @Tool methods."
            );
        }
    }

    public List<Map<String, Object>> listTools() {
        if (!mcpProperties.isEnabled()) {
            return List.of();
        }
        return resolvedTools.values()
                .stream()
                .map(this::toProtocolTool)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public Object callTool(String name, Map<String, Object> arguments) {
        if (!mcpProperties.isEnabled()) {
            throw new McpProtocolException(-32000, "MCP gateway is disabled");
        }
        if (!StringUtils.hasText(name)) {
            throw new McpProtocolException(-32602, "name is required");
        }
        ResolvedTool tool = resolvedTools.get(normalize(name));
        if (tool == null) {
            throw new McpProtocolException(-32602, "Unsupported tool: " + name);
        }
        try {
            return tool.invoker().apply(arguments == null ? Map.of() : arguments);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Tool invoke failed: " + tool.name(), ex);
        }
    }

    private Map<String, DiscoveredTool> discoverAnnotatedTools() {
        Map<String, DiscoveredTool> discovered = new LinkedHashMap<>();
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception ex) {
                // 对于懒加载/条件 Bean，发现阶段不强制初始化。
                continue;
            }

            Class<?> targetClass = AopUtils.getTargetClass(bean);
            if (targetClass == null) {
                continue;
            }

            ReflectionUtils.doWithMethods(targetClass, method -> {
                Tool tool = AnnotatedElementUtils.findMergedAnnotation(method, Tool.class);
                if (tool == null) {
                    return;
                }

                Method invocableMethod = AopUtils.selectInvocableMethod(method, bean.getClass());
                String toolName = resolveToolName(tool, method);
                String key = normalize(toolName);
                if (discovered.containsKey(key)) {
                    throw new IllegalStateException("Duplicate @Tool name detected: " + toolName);
                }

                Map<String, Object> schema = generateMethodInputSchema(method);
                Function<Map<String, Object>, Object> invoker = args -> invokeToolMethod(bean, invocableMethod, args);

                discovered.put(key, new DiscoveredTool(
                        toolName,
                        toolName,
                        StringUtils.hasText(tool.description()) ? tool.description().trim() : "",
                        schema,
                        true,
                        true,
                        invoker
                ));
            });
        }
        return discovered;
    }

    private Map<String, McpToolHandler> collectManualHandlers() {
        Map<String, McpToolHandler> manual = new LinkedHashMap<>();
        for (McpToolHandler handler : handlers) {
            String key = normalize(handler.name());
            if (manual.containsKey(key)) {
                throw new IllegalStateException("Duplicate MCP tool handler: " + handler.name());
            }
            manual.put(key, handler);
        }
        return manual;
    }

    private ResolvedTool resolveFromConfig(McpProperties.ToolDefinition definition,
                                           DiscoveredTool discovered,
                                           McpToolHandler manualHandler) {
        if (discovered == null && manualHandler == null) {
            throw new IllegalStateException("No @Tool or McpToolHandler found for config tool: " + definition.getName());
        }

        String name = definition.getName().trim();
        String title = StringUtils.hasText(definition.getTitle())
                ? definition.getTitle().trim()
                : discovered != null ? discovered.title() : name;
        String description = StringUtils.hasText(definition.getDescription())
                ? definition.getDescription().trim()
                : discovered != null ? discovered.description() : "";

        Map<String, Object> schema = (definition.getInputSchema() != null && !definition.getInputSchema().isEmpty())
                ? definition.getInputSchema()
                : discovered != null ? discovered.inputSchema() : Map.of("type", "object", "properties", Map.of());

        boolean readOnlyHint = definition.getReadOnlyHint() != null
                ? definition.getReadOnlyHint()
                : discovered != null && discovered.readOnlyHint();
        boolean idempotentHint = definition.getIdempotentHint() != null
                ? definition.getIdempotentHint()
                : discovered != null && discovered.idempotentHint();

        Function<Map<String, Object>, Object> invoker = discovered != null
                ? discovered.invoker()
                : manualHandler::execute;

        return new ResolvedTool(name, title, description, schema, readOnlyHint, idempotentHint, SOURCE_CONFIG_OVERRIDE, invoker);
    }

    private void autoRegisterDiscoveredTools(Map<String, DiscoveredTool> discoveredTools, LinkedHashSet<String> configuredNames) {
        discoveredTools.values()
                .stream()
                .sorted(Comparator.comparing(DiscoveredTool::name))
                .forEach(discovered -> {
                    String key = normalize(discovered.name());
                    if (configuredNames.contains(key) || resolvedTools.containsKey(key)) {
                        return;
                    }
                    log.info("Auto-register MCP tool from @Tool: {}", discovered.name());
                    resolvedTools.put(key, new ResolvedTool(
                            discovered.name(),
                            discovered.title(),
                            discovered.description(),
                            discovered.inputSchema(),
                            discovered.readOnlyHint(),
                            discovered.idempotentHint(),
                            SOURCE_AUTO_TOOL,
                            discovered.invoker()
                    ));
                });
    }

    private void autoRegisterManualHandlers(Map<String, McpToolHandler> manualHandlers, LinkedHashSet<String> configuredNames) {
        manualHandlers.values()
                .stream()
                .sorted(Comparator.comparing(McpToolHandler::name))
                .forEach(handler -> {
                    String key = normalize(handler.name());
                    if (configuredNames.contains(key) || resolvedTools.containsKey(key)) {
                        return;
                    }
                    log.info("Auto-register MCP tool from handler: {}", handler.name());
                    resolvedTools.put(key, new ResolvedTool(
                            handler.name(),
                            handler.name(),
                            "",
                            Map.of("type", "object", "properties", Map.of()),
                            true,
                            true,
                            SOURCE_MANUAL_HANDLER,
                            handler::execute
                    ));
                });
    }

    private Map<String, Object> toProtocolTool(ResolvedTool tool) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", tool.name());
        if (StringUtils.hasText(tool.title())) {
            result.put("title", tool.title());
        }
        if (StringUtils.hasText(tool.description())) {
            result.put("description", tool.description());
        }
        result.put("source", tool.source());
        result.put("inputSchema", tool.inputSchema());
        result.put("annotations", Map.of(
                "readOnlyHint", tool.readOnlyHint(),
                "idempotentHint", tool.idempotentHint(),
                "registrationSource", tool.source()
        ));
        return result;
    }

    private String resolveToolName(Tool tool, Method method) {
        if (StringUtils.hasText(tool.name())) {
            return tool.name().trim();
        }
        return method.getName();
    }

    private Map<String, Object> generateMethodInputSchema(Method method) {
        String schema = JsonSchemaGenerator.generateForMethodInput(method);
        if (!StringUtils.hasText(schema)) {
            return Map.of("type", "object", "properties", Map.of());
        }
        try {
            return objectMapper.readValue(schema, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse @Tool schema for method: " + method, ex);
        }
    }

    private Object invokeToolMethod(Object bean, Method method, Map<String, Object> arguments) {
        try {
            Object[] invokeArgs = buildInvokeArgs(method, arguments);
            return method.invoke(bean, invokeArgs);
        } catch (InvocationTargetException ex) {
            Throwable target = ex.getTargetException();
            if (target instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(target);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Tool method invoke failed: " + method, ex);
        }
    }

    private Object[] buildInvokeArgs(Method method, Map<String, Object> arguments) {
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            return new Object[0];
        }

        Object[] args = new Object[parameters.length];
        if (parameters.length == 1) {
            Parameter parameter = parameters[0];
            String parameterName = parameter.getName();
            Class<?> parameterType = parameter.getType();
            boolean containsParamName = arguments.containsKey(parameterName);

            if (containsParamName && isSimpleType(parameterType)) {
                args[0] = objectMapper.convertValue(arguments.get(parameterName), parameterType);
            } else if (Map.class.isAssignableFrom(parameterType)) {
                args[0] = arguments;
            } else if (containsParamName && arguments.size() == 1) {
                args[0] = objectMapper.convertValue(arguments.get(parameterName), parameterType);
            } else {
                args[0] = objectMapper.convertValue(arguments, parameterType);
            }
            return args;
        }

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Object rawValue = arguments.get(parameter.getName());
            args[i] = objectMapper.convertValue(rawValue, parameter.getType());
        }
        return args;
    }

    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || Number.class.isAssignableFrom(type)
                || CharSequence.class.isAssignableFrom(type)
                || Boolean.class == type
                || Objects.equals(type, Integer.TYPE)
                || Objects.equals(type, Long.TYPE)
                || Objects.equals(type, Double.TYPE)
                || Objects.equals(type, Float.TYPE)
                || Objects.equals(type, Boolean.TYPE);
    }

    private String normalize(String value) {
        return value.trim().toLowerCase();
    }

    private record DiscoveredTool(
            String name,
            String title,
            String description,
            Map<String, Object> inputSchema,
            boolean readOnlyHint,
            boolean idempotentHint,
            Function<Map<String, Object>, Object> invoker
    ) {
    }

    private record ResolvedTool(
            String name,
            String title,
            String description,
            Map<String, Object> inputSchema,
            boolean readOnlyHint,
            boolean idempotentHint,
            String source,
            Function<Map<String, Object>, Object> invoker
    ) {
    }
}
