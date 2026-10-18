package com.scchan.warpplus;

import java.util.UUID;

public record Actor(UUID playerId, boolean admin, boolean canRegister, boolean canRegisterRoot) {}
