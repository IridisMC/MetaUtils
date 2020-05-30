package descriptor

// Comes directly from the spec https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.2
typealias FieldDescriptor = FieldType
typealias AnyType = FieldType


sealed class Descriptor(val classFileName: String) {
    override fun equals(other: Any?) = other is Descriptor && other.classFileName == classFileName
    override fun hashCode() = classFileName.hashCode()
}


sealed class ReturnDescriptor(classFileName: String) : Descriptor(classFileName) {
    object Void : ReturnDescriptor("V") {
        override fun toString() = "void"
    }
}

sealed class FieldType(classFileName: String) : ReturnDescriptor(classFileName) {
    companion object

}

sealed class PrimitiveType(classFileName: String) : FieldType(classFileName) {
    object Byte : PrimitiveType("B") {
        override fun toString() = "byte"
    }

    object Char : PrimitiveType("C") {
        override fun toString() = "char"
    }

    object Double : PrimitiveType("D") {
        override fun toString() = "double"
    }

    object Float : PrimitiveType("F") {
        override fun toString() = "float"
    }

    object Int : PrimitiveType("I") {
        override fun toString() = "int"
    }

    object Long : PrimitiveType("J") {
        override fun toString() = "long"
    }

    object Short : PrimitiveType("S") {
        override fun toString() = "short"
    }

    object Boolean : PrimitiveType("Z") {
        override fun toString() = "boolean"
    }
}

/**
 * ObjectTypes use slash/separated/format, always!
 */
data class ObjectType(val className: String) : FieldType("L$className;") {
    override fun toString() = simpleName()

    companion object {
        fun dotQualified(className: String) = ObjectType(className.replace(".", "/"))
    }
}

fun ObjectType.packageName() = className.substring(0, className.lastIndexOf("/").let { if (it == -1) 0 else it })
    .replace("/", ".")

fun ObjectType.simpleName() = className.substring(className.lastIndexOf("/").let { if (it == -1) 0 else it + 1 })

data class ArrayType(val componentType: FieldType) : FieldType("[" + componentType.classFileName) {
    override fun toString() = "$componentType[]"
}

typealias ParameterDescriptor = FieldType

data class MethodDescriptor(
    val parameterDescriptors: List<ParameterDescriptor>,
    val returnDescriptor: ReturnDescriptor
) : Descriptor("(${parameterDescriptors.joinToString("") { it.classFileName }})${returnDescriptor.classFileName}") {
    companion object;
    override fun toString() = "(${parameterDescriptors.joinToString(", ")}): $returnDescriptor"
}