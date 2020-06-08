package codegeneration

import api.GenericJavaType
import api.JavaType
import com.squareup.javapoet.*
import descriptor.*

internal sealed class CodeWriter {
    /**
     * Note: This is a pure operation, I just thing "CodeWriter" is the most fitting name.
     */
    abstract fun write(code: Code): FormattedString
}


//internal object KotlinCodeWriter : JavaCodeWriter() {
//    override fun write(code: Code): FormattedString  = when(code){
//        is Expression.Cast -> "".format
//        else -> super.write(code)
//    }
//}

internal open class JavaCodeWriter : CodeWriter() {
    override fun write(code: Code): FormattedString = when (code) {
        is Expression.Variable -> code.name.format
        is Expression.Cast -> write(code.target).mapString { "($TYPE_FORMAT)$it" }
            .prependArg(code.castTo.toTypeName())
        is Expression.Field -> write(code.owner as Code).mapString { "${it.withParentheses()}.${code.name}" }
        Expression.This -> "this".format
        is Expression.Call -> {
            val (prefixStr, prefixArgs) = code.prefix()
            val parametersCode = code.parameters.map { write(it) }
            val totalArgs = prefixArgs + parametersCode.flatMap { it.formatArguments }
            (prefixStr + "(" + parametersCode.joinToString(", ") { it.string } + ")").format(totalArgs)
        }
        is Statement.Return -> write(code.target).mapString { "return $it" }
        is ClassReceiver -> TYPE_FORMAT.format(code.type.toTypeName())
        SuperReceiver -> "super".format
        is Statement.Assignment -> write(code.target).mapString { "$it = " } + write(code.assignedValue)
    }

    // Add parentheses to casts and constructor calls
    private fun String.withParentheses() =
        if ((startsWith("(") || startsWith("new")) && !startsWith(")")) "($this)" else this
//    private fun String.removeParentheses() = if (startsWith("(")) substring(1, length - 1) else this

    private fun Expression.Call.prefix(): FormattedString = when (this) {
        is Expression.Call.This -> "this".format
        is Expression.Call.Super -> "super".format
        is Expression.Call.Method -> if (receiver == null) name.format else write(receiver as Code)
            .mapString { "${it.withParentheses()}.$name" }
        is Expression.Call.Constructor -> {
            val rightSide = "new $TYPE_FORMAT".format(constructing.toTypeName())
            if (receiver == null) rightSide
            else write(receiver).mapString { "${it.withParentheses()}." } + rightSide
        }
    }

}

private fun ObjectType.toTypeName(): ClassName {
//    val className = simpleName()
//    val innerClassSplit = className.split("\$")
//    val rootClass = innerClassSplit[0]
//    val innerClasses = innerClassSplit.drop(1)
    val shortName = fullClassName.shortName
    return ClassName.get(
        fullClassName.packageName?.toDotQualified() ?: "", shortName.outerClass(),
        *shortName.innerClasses().toTypedArray()
    )
}

private fun JvmType.toTypeName(): TypeName = when (this) {
    PrimitiveType.Byte -> TypeName.BYTE
    PrimitiveType.Char -> TypeName.CHAR
    PrimitiveType.Double -> TypeName.DOUBLE
    PrimitiveType.Float -> TypeName.FLOAT
    PrimitiveType.Int -> TypeName.INT
    PrimitiveType.Long -> TypeName.LONG
    PrimitiveType.Short -> TypeName.SHORT
    PrimitiveType.Boolean -> TypeName.BOOLEAN
    is ObjectType -> this.toTypeName()
    is ArrayType -> ArrayTypeName.of(componentType.toTypeName())
}

internal fun ReturnDescriptor.toTypeName(): TypeName = when (this) {
    is JvmType -> toTypeName()
    ReturnDescriptor.Void -> TypeName.VOID
}

internal fun GenericJavaType.toTypeName(): TypeName = when (this) {
    //TODO: annotations with annotated()
    is JavaType.Generic -> with(declaration) {
        TypeVariableName.get(name, *upperBounds.map { it.toTypeName() }.toTypedArray())
    }
    is JavaType.Class<*> -> if (typeArguments.isEmpty()) rawType.toTypeName() else {
        ParameterizedTypeName.get(rawType.toTypeName(), *typeArguments.map { it.toTypeName() }.toTypedArray())
    }
    is GenericJavaType.Wildcard -> {
        val boundType = bound.toTypeName()
        if (boundsAreUpper) WildcardTypeName.subtypeOf(boundType) else WildcardTypeName.supertypeOf(boundType)
    }
}

//TODO: replace with normal string?
internal data class FormattedString(val string: String, val formatArguments: List<TypeName>) {
    fun mapString(map: (String) -> String) = copy(string = map(string))
    fun appendArg(arg: TypeName) = copy(formatArguments = formatArguments + arg)
    fun prependArg(arg: TypeName) = copy(formatArguments = listOf(arg) + formatArguments)

    //    operator fun plus(appended: String) = FormattedString(this.string + appended, formatArguments)
    operator fun plus(other: FormattedString) =
        FormattedString(this.string + other.string, formatArguments + other.formatArguments)
}

//internal operator fun String.plus(formatted: FormattedString) =
//    FormattedString(this + formatted.string, formatted.formatArguments)

private val String.format get() = FormattedString(this, listOf())
private fun String.format(args: List<TypeName>) = FormattedString(this, args)
private fun String.format(arg: TypeName) = FormattedString(this, listOf(arg))

//private fun SelfConstructorType.toJavaCode() = when (this) {
//    SelfConstructorType.This -> "this"
//    SelfConstructorType.Super -> "super"
//}


private const val TYPE_FORMAT = "\$T"

