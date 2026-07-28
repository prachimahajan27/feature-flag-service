package com.example.feature_flag_service.DTO;

public record EvalResponse(String flag, String user, boolean on) {}
