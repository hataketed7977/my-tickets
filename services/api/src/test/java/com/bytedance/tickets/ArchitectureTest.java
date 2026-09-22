package com.bytedance.tickets;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(
        packages = "com.bytedance.tickets",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    /**
     * 分层依赖单向：controller → service → repository。
     * model / exception / security / config 为共享包，不参与分层约束。
     */
    @ArchTest
    static final ArchRule layeredDependenciesAreUnidirectional =
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Controller").definedBy("..controller..")
                    .layer("Service").definedBy("..service..")
                    .layer("Repository").definedBy("..repository..")
                    .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
                    .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service");

    /**
     * 带 @Entity 的类，类名必须以 Entity 结尾。
     */
    @ArchTest
    static final ArchRule entityClassesMustEndWithEntity =
            classes()
                    .that().areAnnotatedWith("jakarta.persistence.Entity")
                    .should().haveSimpleNameEndingWith("Entity")
                    .allowEmptyShould(true);
}
