package com.bsse2018.salavatov.flt.cykquery.cli

import com.bsse2018.salavatov.flt.algorithms.CYKQuery
import com.bsse2018.salavatov.flt.grammars.ContextFreeGrammar
import com.bsse2018.salavatov.flt.grammars.EmptyLanguageException
import java.io.File
import java.lang.Exception

fun main(args: Array<String>) {
    if (args.size != 2) {
        println("Arguments: <grammar file> <query file>")
        return
    }
    val rawGrammar = File(args[0]).readLines().filter { it.isNotEmpty() }
    val grammar = ContextFreeGrammar.fromStrings(rawGrammar)
    val query = File(args[1]).readLines().getOrElse(0) { "" }.trim()
    try {
        val cnf = grammar.toChomskyNormalForm()
        println(CYKQuery(cnf, query.map { it.toString() }))
    } catch (e: EmptyLanguageException) {
        println(query == "")
    } catch (e: Exception) {
        println("ERROR: ${e.message}")
    }
}