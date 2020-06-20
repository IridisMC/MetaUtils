package abstractor

import api.JavaType
import api.remap
import descriptor.ReturnDescriptor
import descriptor.remap
import signature.ClassGenericType
import signature.GenericReturnType
import signature.TypeArgumentDeclaration
import signature.remap
import util.*

class VersionPackage(private val versionPackage: String) {
    private fun PackageName?.toApiPackageName() = versionPackage.prependToQualified(this ?: PackageName.Empty)
    private fun ShortClassName.toApiShortClassName() =
        ShortClassName(("I" + outerMostClass()).prependTo(innerClasses()))

//    fun String.remapToApiClass(dotQualified : Boolean = false, dollarQualified : Boolean = true) =
//        toQualifiedName(dotQualified, dollarQualified).toApiClass().toString(dotQualified, dollarQualified)

    fun QualifiedName.toApiClass(): QualifiedName = if (isMcClassName()) {
        QualifiedName(
            packageName = packageName.toApiPackageName(),
            shortName = shortName.toApiShortClassName()
        )
    } else this

    private fun ShortClassName.toBaseShortClassName() =
        ShortClassName(("Base" + outerMostClass()).prependTo(innerClasses()))

//    private fun ShortClassName.toBaseApiShortClassName() =
//        ShortClassName(("IBase" + outerMostClass()).prependTo(innerClasses()))

    fun QualifiedName.toBaseClass(): QualifiedName = if (isMcClassName()) {
        QualifiedName(
            packageName = packageName.toApiPackageName(),
            shortName = shortName.toBaseShortClassName()
        )
    } else this

//    fun QualifiedName.toBaseApiInterface(): QualifiedName = if (isMcClassName()) {
//        QualifiedName(
//            packageName = packageName.toApiPackageName(),
//            shortName = shortName.toBaseApiShortClassName()
//        )
//    } else this

    fun <T : ReturnDescriptor>T.remapToApiClass() : T = remap { it.toApiClass() }
    fun <T : GenericReturnType> JavaType<T>.remapToApiClass(): JavaType<T> = remap { it.toApiClass() }
    fun <T : GenericReturnType> JavaType<T>.remapToBaseClass(): JavaType<T> = remap { it.toBaseClass() }
    fun <T : GenericReturnType> T.remapToApiClass(): T = remap { it.toApiClass() }
    fun List<TypeArgumentDeclaration>.remapDeclToApiClasses() = map { typeArg ->
        typeArg.copy(
            classBound = typeArg.classBound?.remapToApiClass(),
            interfaceBounds = typeArg.interfaceBounds.map { it.remapToApiClass() })
    }


    fun <T : GenericReturnType> List<JavaType<T>>.remapToApiClasses(): List<JavaType<T>> =
        map { it.remapToApiClass() }
}
 fun PackageName?.isMcPackage(): Boolean = this?.startsWith("net", "minecraft") == true
 fun QualifiedName.isMcClassName(): Boolean = packageName.isMcPackage()
 fun GenericReturnType.isMcClass(): Boolean = this is ClassGenericType && packageName.isMcPackage()
 fun JavaType<*>.isMcClass(): Boolean = type.isMcClass()