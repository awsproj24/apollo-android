package com.apollographql.apollo3.compiler.codegen.java

import com.apollographql.apollo3.compiler.APOLLO_VERSION
import com.apollographql.apollo3.compiler.CodegenMetadata
import com.apollographql.apollo3.compiler.CodegenSchema
import com.apollographql.apollo3.compiler.CommonCodegenOptions
import com.apollographql.apollo3.compiler.JavaCodegenOptions
import com.apollographql.apollo3.compiler.JavaNullable
import com.apollographql.apollo3.compiler.MODELS_OPERATION_BASED
import com.apollographql.apollo3.compiler.PackageNameGenerator
import com.apollographql.apollo3.compiler.TargetLanguage
import com.apollographql.apollo3.compiler.codegen.CodegenLayout
import com.apollographql.apollo3.compiler.codegen.ResolverKey
import com.apollographql.apollo3.compiler.codegen.ResolverKeyKind
import com.apollographql.apollo3.compiler.codegen.java.adapter.EnumResponseAdapterBuilder
import com.apollographql.apollo3.compiler.codegen.java.operations.FragmentBuilder
import com.apollographql.apollo3.compiler.codegen.java.operations.FragmentDataAdapterBuilder
import com.apollographql.apollo3.compiler.codegen.java.operations.FragmentModelsBuilder
import com.apollographql.apollo3.compiler.codegen.java.operations.FragmentSelectionsBuilder
import com.apollographql.apollo3.compiler.codegen.java.operations.FragmentVariablesAdapterBuilder
import com.apollographql.apollo3.compiler.codegen.java.operations.OperationBuilder
import com.apollographql.apollo3.compiler.codegen.java.operations.OperationResponseAdapterBuilder
import com.apollographql.apollo3.compiler.codegen.java.operations.OperationSelectionsBuilder
import com.apollographql.apollo3.compiler.codegen.java.operations.OperationVariablesAdapterBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.BuilderFactoryBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.EnumAsClassBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.EnumAsEnumBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.InputObjectAdapterBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.InputObjectBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.InterfaceBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.InterfaceBuilderBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.InterfaceMapBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.InterfaceUnknownMapBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.ObjectBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.ObjectBuilderBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.ObjectMapBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.ScalarBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.SchemaBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.UnionBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.UnionBuilderBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.UnionMapBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.UnionUnknownMapBuilder
import com.apollographql.apollo3.compiler.codegen.java.schema.UtilAssertionsBuilder
import com.apollographql.apollo3.compiler.compilerJavaHooks
import com.apollographql.apollo3.compiler.defaultClassesForEnumsMatching
import com.apollographql.apollo3.compiler.defaultDecapitalizeFields
import com.apollographql.apollo3.compiler.defaultGenerateFragmentImplementations
import com.apollographql.apollo3.compiler.defaultGenerateModelBuilders
import com.apollographql.apollo3.compiler.defaultGeneratePrimitiveTypes
import com.apollographql.apollo3.compiler.defaultGenerateQueryDocument
import com.apollographql.apollo3.compiler.defaultGenerateSchema
import com.apollographql.apollo3.compiler.defaultGeneratedSchemaName
import com.apollographql.apollo3.compiler.defaultNullableFieldStyle
import com.apollographql.apollo3.compiler.defaultUseSemanticNaming
import com.apollographql.apollo3.compiler.generateMethodsJava
import com.apollographql.apollo3.compiler.hooks.ApolloCompilerJavaHooks
import com.apollographql.apollo3.compiler.ir.DefaultIrOperations
import com.apollographql.apollo3.compiler.ir.DefaultIrSchema
import com.apollographql.apollo3.compiler.ir.IrOperations
import com.apollographql.apollo3.compiler.ir.IrSchema
import com.apollographql.apollo3.compiler.operationoutput.OperationOutput
import com.apollographql.apollo3.compiler.operationoutput.findOperationId
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.JavaFile

private class OutputBuilder {
  val builders = mutableListOf<JavaClassBuilder>()
}

private fun buildOutput(
    codegenSchema: CodegenSchema,
    upstreamCodegenMetadata: List<CodegenMetadata>,
    generatePrimitiveTypes: Boolean,
    nullableFieldStyle: JavaNullable,
    apolloCompilerJavaHooks: List<ApolloCompilerJavaHooks>?,
    targetLanguage: TargetLanguage,
    block: OutputBuilder.(resolver: JavaResolver) -> Unit,
): JavaOutput {

  @Suppress("NAME_SHADOWING")
  val apolloCompilerJavaHooks = compilerJavaHooks(apolloCompilerJavaHooks)

  val resolver = JavaResolver(
      entries = upstreamCodegenMetadata.flatMap { it.entries },
      next = null,
      scalarMapping = codegenSchema.scalarMapping,
      generatePrimitiveTypes = generatePrimitiveTypes,
      nullableFieldStyle = nullableFieldStyle,
      hooks = apolloCompilerJavaHooks
  )

  val outputBuilder = OutputBuilder()
  outputBuilder.block(resolver)

  outputBuilder.builders.forEach { it.prepare() }
  val fileInfos = outputBuilder.builders
      .map {
        val codegenJavaFile = it.build()

        JavaFile.builder(
            codegenJavaFile.packageName,
            codegenJavaFile.typeSpec
        ).addFileComment(
            """
                
                AUTO-GENERATED FILE. DO NOT MODIFY.
                
                This class was automatically generated by Apollo GraphQL version '$APOLLO_VERSION'.
                
              """.trimIndent()

        ).build()
      }.map {
        ApolloCompilerJavaHooks.FileInfo(javaFile = it)
      }.let {
        apolloCompilerJavaHooks.fold(it as Collection<ApolloCompilerJavaHooks.FileInfo>) { acc, hooks ->
          hooks.postProcessFiles(acc)
        }
      }

  return JavaOutput(
      fileInfos.map { it.javaFile },
      CodegenMetadata(
          targetLanguage = targetLanguage,
          entries = resolver.entries()
      )
  )
}

internal object JavaCodegen {
  fun buildSchemaSources(
      codegenSchema: CodegenSchema,
      irSchema: IrSchema?,
      commonCodegenOptions: CommonCodegenOptions,
      javaCodegenOptions: JavaCodegenOptions,
      packageNameGenerator: PackageNameGenerator,
      compilerJavaHooks: List<ApolloCompilerJavaHooks>,
  ): JavaOutput {

    if (codegenSchema.codegenModels != MODELS_OPERATION_BASED) {
      error("Java codegen does not support ${codegenSchema.codegenModels}. Only $MODELS_OPERATION_BASED is supported.")
    }

    val generateDataBuilders = codegenSchema.generateDataBuilders
    val decapitalizeFields = commonCodegenOptions.decapitalizeFields

    val generateMethods = generateMethodsJava(commonCodegenOptions.generateMethods)
    val generateSchema = commonCodegenOptions.generateSchema ?: defaultGenerateSchema || generateDataBuilders
    val generatedSchemaName = commonCodegenOptions.generatedSchemaName ?: defaultGeneratedSchemaName
    val useSemanticNaming = commonCodegenOptions.useSemanticNaming ?: defaultUseSemanticNaming

    val classesForEnumsMatching = javaCodegenOptions.classesForEnumsMatching ?: defaultClassesForEnumsMatching
    val generateModelBuilders = javaCodegenOptions.generateModelBuilders ?: defaultGenerateModelBuilders
    val generatePrimitiveTypes = javaCodegenOptions.generatePrimitiveTypes ?: defaultGeneratePrimitiveTypes
    val nullableFieldStyle = javaCodegenOptions.nullableFieldStyle ?: defaultNullableFieldStyle

    val scalarMapping = codegenSchema.scalarMapping

    return buildOutput(
        codegenSchema = codegenSchema,
        upstreamCodegenMetadata = emptyList(),
        generatePrimitiveTypes = generatePrimitiveTypes,
        nullableFieldStyle = nullableFieldStyle,
        apolloCompilerJavaHooks = compilerJavaHooks,
        targetLanguage = codegenSchema.targetLanguage,
    ) { resolver ->

      val layout = CodegenLayout(
          codegenSchema = codegenSchema,
          packageNameGenerator = packageNameGenerator,
          useSemanticNaming = useSemanticNaming,
          decapitalizeFields = decapitalizeFields ?: defaultDecapitalizeFields
      )

      val context = JavaContext(
          layout = layout,
          resolver = resolver,
          generateMethods = generateMethodsJava(generateMethods),
          generateModelBuilders = generateModelBuilders,
          nullableFieldStyle = nullableFieldStyle,
      )

      check(irSchema is DefaultIrSchema)
      irSchema.irScalars.forEach { irScalar ->
        builders.add(ScalarBuilder(context, irScalar, scalarMapping.get(irScalar.name)?.targetName))
      }
      irSchema.irEnums.forEach { irEnum ->
        if (classesForEnumsMatching.any { Regex(it).matches(irEnum.name) }) {
          builders.add(EnumAsClassBuilder(context, irEnum))
        } else {
          builders.add(EnumAsEnumBuilder(context, irEnum))
        }
        builders.add(EnumResponseAdapterBuilder(context, irEnum))
      }
      irSchema.irInputObjects.forEach { irInputObject ->
        builders.add(InputObjectBuilder(context, irInputObject))
        builders.add(InputObjectAdapterBuilder(context, irInputObject))
      }
      if (context.nullableFieldStyle == JavaNullable.GUAVA_OPTIONAL && irSchema.irInputObjects.any { it.isOneOf }) {
        // When using the Guava optionals, generate assertOneOf in the project, as apollo-api doesn't depend on Guava
        builders.add(UtilAssertionsBuilder(context))
      }
      irSchema.irUnions.forEach { irUnion ->
        builders.add(UnionBuilder(context, irUnion))
        if (generateDataBuilders) {
          builders.add(UnionBuilderBuilder(context, irUnion))
          builders.add(UnionUnknownMapBuilder(context, irUnion))
          builders.add(UnionMapBuilder(context, irUnion))
        }
      }
      irSchema.irInterfaces.forEach { irInterface ->
        builders.add(InterfaceBuilder(context, irInterface))
        if (generateDataBuilders) {
          builders.add(InterfaceBuilderBuilder(context, irInterface))
          builders.add(InterfaceUnknownMapBuilder(context, irInterface))
          builders.add(InterfaceMapBuilder(context, irInterface))
        }
      }
      irSchema.irObjects.forEach { irObject ->
        builders.add(ObjectBuilder(context, irObject))
        if (generateDataBuilders) {
          builders.add(ObjectBuilderBuilder(context, irObject))
          builders.add(ObjectMapBuilder(context, irObject))
        }
      }
      if (generateSchema && context.resolver.resolve(ResolverKey(ResolverKeyKind.Schema, "")) == null) {
        builders.add(SchemaBuilder(context, generatedSchemaName, scalarMapping, irSchema.irObjects, irSchema.irInterfaces, irSchema.irUnions, irSchema.irEnums))
      }
      if (generateDataBuilders) {
        builders.add(BuilderFactoryBuilder(context, irSchema.irObjects, irSchema.irInterfaces, irSchema.irUnions))
      }
    }
  }

  fun buildOperationsSources(
      codegenSchema: CodegenSchema,
      irOperations: IrOperations,
      operationOutput: OperationOutput,
      upstreamCodegenMetadata: List<CodegenMetadata>,
      commonCodegenOptions: CommonCodegenOptions,
      javaCodegenOptions: JavaCodegenOptions,
      packageNameGenerator: PackageNameGenerator,
      compilerJavaHooks: List<ApolloCompilerJavaHooks>?,
  ): JavaOutput {
    check(irOperations is DefaultIrOperations)

    if (codegenSchema.codegenModels != MODELS_OPERATION_BASED) {
      error("Java codegen does not support ${codegenSchema.codegenModels}. Only $MODELS_OPERATION_BASED is supported.")
    }
    if (!irOperations.flattenModels) {
      error("Java codegen does not support nested models as it could trigger name clashes when a nested class has the same name as an " +
          "enclosing one.")
    }

    val flatten = irOperations.flattenModels
    val generateDataBuilders = irOperations.generateDataBuilders
    val decapitalizeFields = irOperations.decapitalizeFields

    val generateFragmentImplementations = commonCodegenOptions.generateFragmentImplementations ?: defaultGenerateFragmentImplementations
    val generateMethods = generateMethodsJava(commonCodegenOptions.generateMethods)
    val generateQueryDocument = commonCodegenOptions.generateQueryDocument ?: defaultGenerateQueryDocument
    val useSemanticNaming = commonCodegenOptions.useSemanticNaming ?: defaultUseSemanticNaming

    val generateModelBuilders = javaCodegenOptions.generateModelBuilders ?: defaultGenerateModelBuilders
    val generatePrimitiveTypes = javaCodegenOptions.generatePrimitiveTypes ?: defaultGeneratePrimitiveTypes
    val nullableFieldStyle = javaCodegenOptions.nullableFieldStyle ?: defaultNullableFieldStyle

    return buildOutput(
        codegenSchema = codegenSchema,
        upstreamCodegenMetadata = upstreamCodegenMetadata,
        generatePrimitiveTypes = generatePrimitiveTypes,
        nullableFieldStyle = nullableFieldStyle,
        apolloCompilerJavaHooks = compilerJavaHooks,
        targetLanguage = codegenSchema.targetLanguage,
    ) { resolver ->

      val layout = CodegenLayout(
          codegenSchema = codegenSchema,
          packageNameGenerator = packageNameGenerator,
          useSemanticNaming = useSemanticNaming,
          decapitalizeFields = decapitalizeFields
      )

      val context = JavaContext(
          layout = layout,
          resolver = resolver,
          generateMethods = generateMethodsJava(generateMethods),
          generateModelBuilders = generateModelBuilders,
          nullableFieldStyle = nullableFieldStyle,
      )

      irOperations.fragments
          .forEach { fragment ->
            builders.add(
                FragmentModelsBuilder(
                    context,
                    fragment,
                    (fragment.interfaceModelGroup ?: fragment.dataModelGroup),
                    fragment.interfaceModelGroup == null,
                    flatten,
                )
            )

            builders.add(FragmentSelectionsBuilder(context, fragment))

            if (generateFragmentImplementations || fragment.interfaceModelGroup == null) {
              builders.add(FragmentDataAdapterBuilder(context, fragment, flatten))
            }

            if (generateFragmentImplementations) {
              builders.add(
                  FragmentBuilder(
                      context,
                      fragment,
                      flatten,
                  )
              )
              if (fragment.variables.isNotEmpty()) {
                builders.add(FragmentVariablesAdapterBuilder(context, fragment))
              }
            }
          }

      irOperations.operations
          .forEach { operation ->
            if (operation.variables.isNotEmpty()) {
              builders.add(OperationVariablesAdapterBuilder(context, operation))
            }

            builders.add(OperationSelectionsBuilder(context, operation))
            builders.add(OperationResponseAdapterBuilder(context, operation, flatten))

            builders.add(
                OperationBuilder(
                    context = context,
                    operationId = operationOutput.findOperationId(operation.name),
                    generateQueryDocument = generateQueryDocument,
                    operation = operation,
                    flatten = flatten,
                    generateDataBuilders = generateDataBuilders
                )
            )
          }
    }
  }
}

fun List<CodeBlock>.joinToCode(separator: String, prefix: String = "", suffix: String = ""): CodeBlock {
  var first = true
  return fold(
      CodeBlock.builder().add(prefix)
  ) { builder, block ->
    if (first) {
      first = false
    } else {
      builder.add(separator)
    }
    builder.add(L, block)
  }.add(suffix)
      .build()
}

fun CodeBlock.isNotEmpty() = isEmpty().not()

internal const val T = "${'$'}T"
internal const val L = "${'$'}L"
internal const val S = "${'$'}S"