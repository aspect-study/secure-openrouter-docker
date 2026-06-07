package com.openrouter.gateway.agent.tool;

import java.util.Map;

/**
 * A read-only tool the Gateway Intelligence Agent can call.
 * input_schema follows JSON Schema (type/properties/required) per the Claude API tool format —
 * see PRD-005 for the full Claude ↔ OpenAI-compatible mapping this supports.
 */
public interface GatewayTool {

    String name();

    String description();

    Map<String, Object> inputSchema();

    Map<String, Object> execute(Map<String, Object> input);
}
