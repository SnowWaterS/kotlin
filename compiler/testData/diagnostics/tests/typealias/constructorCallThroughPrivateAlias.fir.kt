// !DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_VARIABLE
// !CHECK_TYPE
// FILE: a.kt
package a

class B(x: String)
typealias A1 = B
private typealias A2 = B
private typealias A3 = B

fun A3(x: Any) = "OK"

fun bar() {
    A3("") checkType { <!UNRESOLVED_REFERENCE!>_<!><B>() }
}

// FILE: main.kt
package usage

import a.B

fun baz() {
    a.A1("") // resolved to B constructor, OK
    a.A2("") // resolved to B constructor, INVISIBLE_MEMBER because type alias is private, OK

    a.A3("") checkType { <!UNRESOLVED_REFERENCE!>_<!><String>() }

    val x: a.A2 = B("") // A2 is unresolved because it's private in file, OK
}
