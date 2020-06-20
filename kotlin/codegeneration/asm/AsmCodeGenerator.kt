package codegeneration.asm

import api.AnyJavaType
import api.JavaReturnType
import api.JavaType
import codegeneration.*
import descriptor.JavaLangObjectJvmType
import descriptor.MethodDescriptor
import descriptor.ObjectType
import descriptor.ReturnDescriptor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import signature.*
import util.*
import java.nio.file.Path


private fun GenericReturnType.hasAnyTypeArguments() = getContainedClassesRecursively().size > 1
internal fun JavaReturnType.asmType(): Type = toJvmType().asmType()
internal fun ReturnDescriptor.asmType(): Type = Type.getType(classFileName)

private fun writeClassImpl(
    info: ClassInfo, className: QualifiedName, srcRoot: Path, index: ClasspathIndex
): Unit = with(info) {


    val genericsInvolved = typeArguments.isNotEmpty() || superClass?.type?.hasAnyTypeArguments() == true
            || superInterfaces.any { it.type.hasAnyTypeArguments() }
    val signature = if (genericsInvolved) ClassSignature(typeArguments = typeArguments,
        superClass = superClass?.type ?: JavaLangObjectGenericType,
        superInterfaces = superInterfaces.map { it.type }
    ) else null


    val classWriter = AsmClassWriter(index)

    classWriter.writeClass(
        access, visibility, className,
        //TODO: investigate if we can trick the IDE to think the original source file is the mc source file
        sourceFile = className.shortName.outerMostClass() + ".java",
        signature = signature,
        superClass = superClass?.toJvmType() ?: JavaLangObjectJvmType,
        superInterfaces = superInterfaces.map { it.toJvmType() },
        annotations = info.annotations
    ) {
        AsmGeneratedClass(
            this,
            className,
            srcRoot,
            info.access.variant.isInterface,
            index
        ).apply(body).finish()
    }


    val path = srcRoot.resolve(className.toPath(".class"))
    path.createParentDirectories()
    classWriter.writeBytesTo(path)
}


class AsmCodeGenerator(private val index: ClasspathIndex) : CodeGenerator {
    override fun writeClass(info: ClassInfo, packageName: PackageName?, srcRoot: Path) {
        writeClassImpl(
            info,
            QualifiedName(packageName, ShortClassName(listOf(info.shortName))),
            srcRoot,
            index
        )
    }

}


private fun <T : GenericReturnType> Iterable<JavaType<T>>.generics() = map { it.type }



private class AsmGeneratedClass(
    private val classWriter: AsmClassWriter.ClassBody,
    private val className: QualifiedName,
    private val srcRoot: Path,
    private val isInterface: Boolean,
    private val index: ClasspathIndex
) : GeneratedClass {

    private val instanceFieldInitializers: MutableMap<FieldExpression, Expression> = mutableMapOf()
    private val staticFieldInitializers: MutableMap<FieldExpression, Expression> = mutableMapOf()
    private val constructors: MutableList<MethodInfo> = mutableListOf()

//    fun addAsmInnerClasses(innerClasses: List<InnerClassNode>) {
//        innerClasses.forEach { classWriter.visitInnerClass(it.name, it.outerName, it.innerName, it.access) }
//    }

    fun finish() {
        assert(instanceFieldInitializers.isEmpty() || constructors.isNotEmpty())
        for (constructor in constructors) {
            constructor.addMethodImpl(
                returnType = VoidJavaType,
                name = ConstructorsName,
                typeArguments = listOf(),
                access = MethodAccess(
                    isStatic = false, visibility = constructor.visibility, isFinal = false, isAbstract = false
                )
            ) {
                for ((targetField, fieldValue) in this@AsmGeneratedClass.instanceFieldInitializers) {
                    addStatement(
                        AssignmentStatement(
                            target = targetField,
                            assignedValue = fieldValue
                        )
                    )
                }
            }
        }

        if (staticFieldInitializers.isNotEmpty()) {
            val staticInitializer = MethodInfo(
                visibility = Visibility.Package,
                parameters = mapOf(),
                throws = listOf()
            ) {
                for ((targetField, fieldValue) in this@AsmGeneratedClass.staticFieldInitializers) {
                    addStatement(
                        AssignmentStatement(
                            target = targetField,
                            assignedValue = fieldValue
                        )
                    )
                }
            }
            staticInitializer.addMethodImpl(
                returnType = VoidJavaType,
                name = "<clinit>",
                typeArguments = listOf(),
                access = MethodAccess(
                    isStatic = true,
                    visibility = Visibility.Package,
                    isFinal = false,
                    isAbstract = false
                )
            )
        }
    }

    override fun addInnerClass(info: ClassInfo, isStatic: Boolean) {
        val innerClassName = className.innerClass(info.shortName)
        classWriter.trackInnerClass(innerClassName)
        writeClassImpl(info, innerClassName, srcRoot,index)
//        classWriter.visitNestMember(innerClassName.toSlashQualifiedString())
    }

    override fun addMethod(
        methodInfo: MethodInfo,
        isStatic: Boolean,
        isFinal: Boolean,
        isAbstract: Boolean,
        typeArguments: List<TypeArgumentDeclaration>,
        name: String,
        returnType: JavaReturnType
    ) {
        methodInfo.addMethodImpl(
            returnType, typeArguments,
            MethodAccess(isStatic, isFinal, isAbstract, methodInfo.visibility), name
        )
    }


    private fun MethodInfo.addMethodImpl(
        returnType: JavaReturnType,
        typeArguments: List<TypeArgumentDeclaration>,
        access: MethodAccess,
        name: String,
        bodyPrefix: GeneratedMethod.() -> Unit = {}
    ) {
        val descriptor = MethodDescriptor(parameters.values.map { it.toJvmType() }, returnType.toJvmType())
        val genericsInvolved = typeArguments.isNotEmpty() || parameters.values.any { it.type.hasAnyTypeArguments() }
                || returnType.type.hasAnyTypeArguments()
        val signature = if (genericsInvolved) {
            MethodSignature(typeArguments, parameters.values.generics(), returnType.type, throws.generics())
        } else null

        classWriter.writeMethod(
            name, access, descriptor, signature,
            annotations = returnType.annotations,
            parameterAnnotations = parameters.values
                .mapIndexed { i, paramType -> i to paramType.annotations }.toMap()
        ) {
            if (access.isAbstract) {
                AbstractGeneratedMethod.apply(bodyPrefix).apply(body)
            } else {
                val builder = AsmGeneratedMethod(this, name, access.isStatic,
                    parameters.mapValues { (_, type) -> type.toJvmType() }).apply(bodyPrefix).apply(body)

                // This assumes extremely simplistic method calls that just call one method and that's it
                writeZeroOperandInstruction(returnType.asmType().getOpcode(Opcodes.IRETURN))
                val localVarSize = parameters.size.applyIf(!access.isStatic) { it + 1 }
                val stackSize = builder.maxStackSize()
                setMaxStackAndVariablesSize(stackSize, localVarSize)
            }
        }
    }


    override fun addConstructor(info: MethodInfo) {
        require(!isInterface) { "Interfaces cannot have constructors" }
        // Need field initializer information before we write the constructors
        constructors.add(info)
    }


    override fun addField(
        name: String,
        type: AnyJavaType,
        visibility: Visibility,
        isStatic: Boolean,
        isFinal: Boolean,
        initializer: Expression?
    ) {
        val genericsInvolved = type.type.hasAnyTypeArguments()
        val signature = if (genericsInvolved) type.type else null
        classWriter.writeField(
            name, type.toJvmType(), signature, visibility, isStatic, isFinal,
            annotations = type.annotations
        )

        if (initializer != null) {
            val targetField = FieldExpression(
                receiver = if (isStatic) ClassReceiver(ObjectType(className)) else ThisExpression,
                name = name,
                owner = ObjectType(className),
                type = type.toJvmType()
            )
            if (isStatic) {
                staticFieldInitializers[targetField] = initializer
            } else {
                instanceFieldInitializers[targetField] = initializer
            }
        }
    }

}



object AbstractGeneratedMethod : GeneratedMethod {
    override fun addStatement(statement: Statement) {
        error("Method is abstract")
    }

    override fun addComment(comment: String) {
        error("Method is abstract")
    }

}
