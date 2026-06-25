package com.shake.openapi.ai.callback;

import com.shake.openapi.ai.http.OperationExecutor;
import com.shake.openapi.ai.model.OpenApiOperation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Adapts one {@link OpenApiOperation} to a Spring AI {@link ToolCallback}. The
 * tool definition is built once from the operation; {@code call()} stays thin and
 * delegates HTTP execution to {@link OperationExecutor}.
 */
public class OpenApiToolCallback implements ToolCallback
{

    private final OpenApiOperation operation;
    private final OperationExecutor executor;
    private final ToolDefinition toolDefinition;

    public OpenApiToolCallback(OpenApiOperation operation, OperationExecutor executor)
    {
        this.operation = operation;
        this.executor = executor;
        this.toolDefinition = ToolDefinition.builder()
                                            .name(operation.operationId())
                                            .description(operation.summary() != null ? operation.summary() :
                                                                 operation.operationId())
                                            .inputSchema(operation.inputSchema())
                                            .build();
    }

    @Override
    public ToolDefinition getToolDefinition()
    {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput)
    {
        return executor.execute(operation, toolInput);
    }
}
