package com.jobtracker.agents;

/**
 * Captures both the parsed result and the raw text exchanged with the model,
 * so the orchestrator can persist input/output to agent_runs for auditability.
 */
public record AgentInvocation<T>(
        String inputText,
        String rawOutput,
        T result
) {}
