package com.siberalt.singularity.strategy.level.linear;

import java.util.List;

public record Result<T extends Number>(
    List<LinearLevel<T>> levels
) {
}
