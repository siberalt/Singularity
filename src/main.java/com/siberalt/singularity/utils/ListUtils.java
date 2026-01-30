package com.siberalt.singularity.utils;

import java.util.List;
import java.util.stream.Stream;

public class ListUtils {
    public static <T> List<T> merge(List<List<T>> lists) {
        return lists.stream()
            .flatMap(List::stream)
            .distinct()
            .toList();
    }

    public static <T> List<T> merge(List<T> list1, List<T> list2) {
        return Stream.concat(list1.stream(), list2.stream())
            .distinct()
            .toList();
    }

    public static <T> List<T> merge(List<T> list1, List<T> list2, List<T> list3) {
        return Stream.concat(Stream.concat(list1.stream(), list2.stream()), list3.stream())
            .distinct()
            .toList();
    }

    public static <T> List<T> merge(List<T> list1, List<T> list2, List<T> list3, List<T> list4) {
        return Stream.concat(
                Stream.concat(list1.stream(), list2.stream()),
                Stream.concat(list3.stream(), list4.stream())
            )
            .distinct()
            .toList();
    }
}
