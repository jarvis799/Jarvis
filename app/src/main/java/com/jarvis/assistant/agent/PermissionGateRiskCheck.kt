package com.jarvis.assistant.agent

import com.jarvis.assistant.agent.permissions.PermissionGate

/**
 * Owner-safety wrapper used by AgentEngine: classify an incoming request and
 * hand back the level plus the line that has to be shown before acting.
 */
fun PermissionGate.riskCheck(action: String): PermissionGate.Decision = PermissionGate.check(action)
