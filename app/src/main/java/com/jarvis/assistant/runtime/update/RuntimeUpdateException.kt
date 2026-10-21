package com.jarvis.assistant.runtime.update

/** Raised for any rejected, malformed, incompatible, unsigned or tampered update. */
class RuntimeUpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)
