// Compile-only stand-ins for the Room annotations used by entities and DAOs, so the pure-Kotlin parts of the app
// (money, domain, repositories, entities/DAO interfaces) and the JVM tests can be compiled and run without the
// Android SDK or Maven. They carry no behaviour. Used only by tools/jvm-harness/run.sh, never by Gradle.
package androidx.room

annotation class Entity(
    val tableName: String = "",
    val indices: Array<Index> = [],
    val foreignKeys: Array<ForeignKey> = [],
    val primaryKeys: Array<String> = [],
)
annotation class ColumnInfo(val name: String = "", val defaultValue: String = "")
annotation class PrimaryKey(val autoGenerate: Boolean = false)
annotation class Index(vararg val value: String = [], val unique: Boolean = false, val name: String = "")
annotation class ForeignKey(
    val entity: kotlin.reflect.KClass<*>,
    val parentColumns: Array<String>,
    val childColumns: Array<String>,
    val onDelete: Int = 1,
    val onUpdate: Int = 1,
) {
    companion object {
        const val NO_ACTION = 1
        const val RESTRICT = 2
        const val SET_NULL = 3
        const val SET_DEFAULT = 4
        const val CASCADE = 5
    }
}
annotation class Dao
annotation class Query(val value: String)
annotation class Insert(val onConflict: Int = 3)
annotation class Update(val onConflict: Int = 3)
annotation class Upsert
annotation class Delete
annotation class Transaction
object OnConflictStrategy {
    const val REPLACE = 1
    const val ABORT = 3
    const val IGNORE = 5
}
