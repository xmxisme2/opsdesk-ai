package com.opsdesk.ai.architecture;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapper SQL 注解禁用规则测试。
 */
class MapperSqlAnnotationPolicyTest {

    private static final List<String> FORBIDDEN_ANNOTATIONS = List.of(
            "@Select", "@Insert", "@Update", "@Delete", "@SelectProvider",
            "@InsertProvider", "@UpdateProvider", "@DeleteProvider"
    );

    @Test
    void mapperInterfacesShouldKeepBusinessSqlInXml() throws Exception {
        Path sourceRoot = Path.of("src", "main", "java");
        try (var paths = Files.walk(sourceRoot)) {
            List<Path> mapperFiles = paths
                    .filter(path -> path.toString().endsWith("Mapper.java"))
                    .toList();
            for (Path mapperFile : mapperFiles) {
                String source = Files.readString(mapperFile, StandardCharsets.UTF_8);
                for (String annotation : FORBIDDEN_ANNOTATIONS) {
                    assertTrue(!source.contains(annotation),
                            mapperFile + " 禁止使用 SQL 注解 " + annotation);
                }
            }
        }
    }
}
