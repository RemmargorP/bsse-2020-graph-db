package com.bsse2018.salavatov.flt.grammars

import java.util.*
import kotlin.collections.HashSet

class EmptyLanguageException : Exception("Grammar is not habitable")

class ContextFreeGrammar(val start: String, rules_: HashSet<Rule>) {
    val rules: HashSet<Rule> = hashSetOf<Rule>()

    init {
        for (rule_ in rules_) {
            rules.add(Rule(
                rule_.from,
                rule_.to
                    .filterNot { it == Epsilon }.toTypedArray()
                    .ifEmpty { arrayOf(Epsilon) }
            ))
        }
    }

    data class Rule(val from: String, val to: Array<String>) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Rule
            if (from != other.from) return false
            if (!to.contentEquals(other.to)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = from.hashCode()
            result = 31 * result + to.contentHashCode()
            return result
        }

        fun isEpsilon() = to.size == 1 && to[0] == Epsilon
        fun isUnit() = to.size == 1 && isNonTerminal(to[0])
        fun isTerminal() = to.all { isTerminal(it) }
    }

    class NodeAccountant {
        val nonTerminals = hashSetOf<String>()
        private var index = 0

        fun consume(node: String) {
            nonTerminals.add(node)
        }

        fun consume(rule: Rule) {
            nonTerminals.add(rule.from)
            nonTerminals.addAll(rule.to.filter { isTerminal(it) })
        }

        fun consume(cfg: ContextFreeGrammar) {
            consume(cfg.start)
            cfg.rules.forEach { consume(it) }
        }

        fun freshNonTerminal(): String {
            while (true) {
                val name = "S$index"
                if (!nonTerminals.contains(name)) {
                    nonTerminals.add(name)
                    index++
                    return name
                }
                index++
            }
        }
    }

    fun shrinkLongRules(): ContextFreeGrammar {
        val accountant = NodeAccountant()
        accountant.consume(this)
        val newRules = hashSetOf<Rule>()
        rules.forEach { rule ->
            val rhs = rule.to.copyOf().toMutableList()
            var lhs = rule.from
            while (rhs.size > 2) {
                val fresh = accountant.freshNonTerminal()
                newRules.add(Rule(lhs, arrayOf(fresh, rhs.last())))
                rhs.removeAt(rhs.size - 1)
                lhs = fresh
            }
            newRules.add(Rule(lhs, rhs.toTypedArray()))
        }
        return ContextFreeGrammar(start, newRules)
    }

    fun hasOnlySmallRules(): Boolean = rules.all { it.to.size <= 2 }

    fun epsilonProducers(): HashSet<String> {
        val produceEps = hashMapOf<String, Boolean>()
        val concernedRules = hashMapOf<String, HashSet<Rule>>()
        val nonProducingNonTerminals = hashMapOf<Rule, HashSet<String>>()

        produceEps[start] = false
        rules.forEach { rule ->
            produceEps[rule.from] = false
            rule.to.forEach { elem ->
                if (isNonTerminal(elem)) {
                    produceEps[elem] = false
                    concernedRules.getOrPut(elem, { hashSetOf() }).add(rule)
                }
                // add even a terminal node so that we won't mark that rule eps-producing
                nonProducingNonTerminals.getOrPut(rule, { hashSetOf() }).add(elem)
            }
        }

        val queue = ArrayDeque<String>()

        nonProducingNonTerminals
            .filter { it.key.isTerminal() }
            .forEach {
                val rule = it.key
                if (produceEps[rule.from] == false) {
                    produceEps[rule.from] = true
                    queue.add(rule.from)
                }
            }

        while (queue.isNotEmpty()) {
            val nonTerm = queue.pop()
            concernedRules[nonTerm]?.forEach {
                nonProducingNonTerminals[it]?.remove(nonTerm)
                if (nonProducingNonTerminals[it]?.isEmpty() == true) {
                    val lhs = it.from
                    if (produceEps[lhs] == false) {
                        produceEps[lhs] = true
                        queue.add(lhs)
                    }
                }
            }
        }

        return produceEps.filter { it.value }.keys.toHashSet()
    }

    fun reduceEpsilonRules(): ContextFreeGrammar {
        if (!hasOnlySmallRules())
            return shrinkLongRules().reduceEpsilonRules()

        val epsilonProducers = epsilonProducers()
        var newStart = start
        val newRules = hashSetOf<Rule>()

        rules.forEach { rule ->
            if (!rule.isEpsilon()) { // drop all eps-rules
                val rhs = rule.to
                if (rhs.size == 1) {
                    newRules.add(rule.copy())
                } else {
                    assert(rhs.size == 2)
                    if (epsilonProducers.contains(rhs[0])) {
                        newRules.add(Rule(rule.from, arrayOf(rhs[1])))
                    }
                    if (epsilonProducers.contains(rhs[1])) {
                        newRules.add(Rule(rule.from, arrayOf(rhs[0])))
                    }
                    newRules.add(rule.copy())
                }
            }
        }
        if (epsilonProducers.contains(start)) {
            val accountant = NodeAccountant()
            accountant.consume(this)
            newStart = accountant.freshNonTerminal()
            newRules.add(Rule(newStart, arrayOf(Epsilon)))
            newRules.add(Rule(newStart, arrayOf(start)))
        }
        return ContextFreeGrammar(newStart, newRules)
    }

    fun isEpsilonReduced(): Boolean {
        return hasOnlySmallRules() && rules.all {
            !it.isEpsilon() || (it.isEpsilon() && it.from == start)
        }
    }

    fun reduceUnitRules(): ContextFreeGrammar {
        if (!isEpsilonReduced())
            return reduceEpsilonRules().reduceUnitRules()

        val nonUnitRules = hashMapOf<String, ArrayList<Rule>>()
        val unitRules = hashMapOf<String, ArrayList<Rule>>()
        val newRules = hashSetOf<Rule>()

        rules.forEach {
            if (!it.isUnit()) {
                nonUnitRules.getOrPut(it.from, { arrayListOf() }).add(it)
            } else {
                unitRules.getOrPut(it.from, { arrayListOf() }).add(it)
            }
        }

        val accountant = NodeAccountant()
        accountant.consume(this)
        accountant.nonTerminals.forEach { origin ->
            val queue = ArrayDeque<String>()
            val visited = hashSetOf<String>()
            queue.add(origin)
            visited.add(origin)
            while (queue.isNotEmpty()) {
                val v = queue.pop()
                nonUnitRules[v]?.forEach {
                    newRules.add(Rule(origin, it.to))
                }
                unitRules[v]?.forEach {
                    if (!visited.contains(it.to[0])) {
                        queue.add(it.to[0])
                        visited.add(it.to[0])
                    }
                }
            }
        }

        return ContextFreeGrammar(start, newRules)
    }

    fun isUnitReduced(): Boolean {
        return isEpsilonReduced() && rules.all { !it.isUnit() }
    }

    fun generatingRules(): HashSet<Rule> {
        assert(isEpsilonReduced())

        val generatingNonTerminals = hashSetOf<String>()
        val concernedRules = hashMapOf<String, HashSet<Rule>>()
        val nonGeneratingNonTerminals = hashMapOf<Rule, HashSet<String>>()

        val queue = ArrayDeque<String>()

        rules.forEach { rule ->
            if (rule.isTerminal()) {
                if (!generatingNonTerminals.contains(rule.from)) {
                    generatingNonTerminals.add(rule.from)
                    queue.add(rule.from)
                }
                // add a rule with empty deps so that we can easily filter wanted rules later
                nonGeneratingNonTerminals.getOrPut(rule, { hashSetOf() })
            } else {
                rule.to.filter { isNonTerminal(it) }
                    .forEach {
                        concernedRules.getOrPut(it, { hashSetOf() }).add(rule)
                        nonGeneratingNonTerminals.getOrPut(rule, { hashSetOf() }).add(it)
                    }
            }
        }

        while (queue.isNotEmpty()) {
            val nonTerm = queue.pop()
            concernedRules[nonTerm]?.forEach { rule ->
                nonGeneratingNonTerminals[rule]?.remove(nonTerm)
                if (nonGeneratingNonTerminals[rule]?.isEmpty() == true) {
                    val lhs = rule.from
                    if (!generatingNonTerminals.contains(lhs)) {
                        generatingNonTerminals.add(lhs)
                        queue.add(lhs)
                    }
                }
            }
        }

        return nonGeneratingNonTerminals.filter { it.value.isEmpty() }.keys.toHashSet()
    }

    fun reduceNonGeneratingRules(): ContextFreeGrammar {
        val generatingRules = generatingRules()
        if (generatingRules.isEmpty() || !generatingRules.any { it.from == start })
            throw EmptyLanguageException()
        return ContextFreeGrammar(start, generatingRules)
    }

    fun reduceUnreachable(): ContextFreeGrammar {
        val queue = ArrayDeque<String>()
        val reachable = hashSetOf<String>()
        queue.add(start)
        reachable.add(start)
        val graph = rules.groupBy { it.from }
        while (queue.isNotEmpty()) {
            val v = queue.pop()
            graph[v]?.forEach { rule ->
                rule.to.filter { isNonTerminal(it) }
                    .forEach {
                        if (!reachable.contains(it)) {
                            reachable.add(it)
                            queue.add(it)
                        }
                    }
            }
        }
        return ContextFreeGrammar(start, rules.filter { reachable.contains(it.from) }.toHashSet())
    }

    fun reduceLongTerminalRules(): ContextFreeGrammar {
        val accountant = NodeAccountant()
        accountant.consume(this)

        val terminalMapping = hashMapOf<String, String>()
        val newRules = hashSetOf<Rule>()

        rules.forEach { rule ->
            if (rule.to.size >= 2) {
                assert(rule.to.size == 2)
                val s1 = rule.to[0]
                val s2 = rule.to[1]
                newRules.add(
                    Rule(
                        rule.from,
                        arrayOf(
                            if (isTerminal(s1)) {
                                terminalMapping.getOrPut(s1, { accountant.freshNonTerminal() })
                            } else {
                                s1
                            },
                            if (isTerminal(s2)) {
                                terminalMapping.getOrPut(s2, { accountant.freshNonTerminal() })
                            } else {
                                s2
                            }
                        )
                    )
                )
            } else {
                newRules.add(rule.copy())
            }
        }
        terminalMapping.forEach {
            newRules.add(Rule(it.value, arrayOf(it.key)))
        }

        return ContextFreeGrammar(start, newRules)
    }

    fun toChomskyNormalForm(): ContextFreeGrammar {
        var grammar = this
        if (rules.any { it.to.contains(start) }) {
            val accountant = NodeAccountant()
            accountant.consume(this)
            val newStart = accountant.freshNonTerminal()
            val newRules = hashSetOf<Rule>()
            rules.forEach { newRules.add(it.copy()) }
            newRules.add(Rule(newStart, arrayOf(start)))
            grammar = ContextFreeGrammar(newStart, newRules)
        }
        return grammar
            .shrinkLongRules()
            .reduceEpsilonRules()
            .reduceUnitRules()
            .reduceNonGeneratingRules()
            .reduceUnreachable()
            .reduceLongTerminalRules()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContextFreeGrammar

        if (start != other.start) return false
        if (rules.toHashSet() != other.rules.toHashSet()) return false

        return true
    }

    override fun hashCode(): Int {
        var result = start.hashCode()
        result = 31 * result + rules.hashCode()
        return result
    }

    companion object {
        const val Epsilon = "eps"
        val terminalMatcher = Regex("$Epsilon|[a-z]+[0-9]*")
        val nonTerminalMatcher = Regex("[A-Z]+[0-9]*")

        fun isTerminal(node: String) = terminalMatcher.matches(node)
        fun isNonTerminal(node: String) = nonTerminalMatcher.matches(node)
    }
}