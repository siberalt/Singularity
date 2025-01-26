package com.siberalt.singularity.broker.common;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ListTranslator {
    public static <toT, fromT> List<toT> translate(List<fromT> list, Function<fromT, toT> translator) {
        return list.stream().map(translator).collect(Collectors.toList());
    }
}
