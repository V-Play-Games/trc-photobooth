## Mission

You are not doing a one-time cleanup or a rewrite. You are running a standing process: each cycle, find the single
highest-leverage design weakness in this codebase, fix it safely, verify it, and record what you did and why — so the
next cycle, possibly a different session or a different agent, can continue without re-deriving your reasoning from
scratch.

A good cycle is small, boring, and fully verified. A bad cycle is a sprawling diff nobody can review. Ten good cycles
beat one heroic one.

## Skill library

Consult these by name at the point they're listed below. Don't front-load all eighteen every cycle — most cycles only
touch a handful.

| Phase     | Skill                           | What it's for                                                                              |
|-----------|---------------------------------|--------------------------------------------------------------------------------------------|
| Orient    | `code-standards`                | Learn this codebase's actual conventions before suggesting anything                        |
| Diagnose  | `aposd-reviewing-module-design` | Find shallow interfaces, information leakage, needless complexity                          |
| Diagnose  | `cc-control-flow-quality`       | Find deep nesting, high cyclomatic complexity, missing guard clauses                       |
| Diagnose  | `ca-architecture-boundaries`    | Find boundary violations and wrong-direction dependencies                                  |
| Diagnose  | `code-clarity-and-docs`         | Find naming, comment, and documentation gaps                                               |
| Diagnose  | `performance-optimization`      | Profile hot paths and flag real bottlenecks, not just structural weaknesses                |
| Select    | `welc-legacy-code`              | Get untested legacy code safely under characterization tests before touching its structure |
| Select    | `clarify`                       | Turn an ambiguous target or fix into concrete questions instead of a guess                 |
| Design    | `aposd-simplifying-complexity`  | Decide where complexity should live; pull it downward, not outward                         |
| Design    | `cc-pseudocode-programming`     | Design the routine in pseudocode before writing real code                                  |
| Design    | `gof-design-patterns`           | Apply a pattern only when its forces genuinely match the symptom                           |
| Design    | `aposd-designing-deep-modules`  | Check the resulting interface is deep, not shallow                                         |
| Design    | `cc-routine-and-class-design`   | Check cohesion, parameter counts, single responsibility                                    |
| Implement | `cc-refactoring-guidance`       | Fix-first-then-refactor discipline; safe refactor workflow                                 |
| Implement | `cc-defensive-programming`      | Barricades, assertions vs. exceptions, boundary validation                                 |
| Implement | `cc-debugging`                  | Root-cause a defect systematically instead of guessing at a fix                            |
| Verify    | `aposd-verifying-correctness`   | Check functional correctness, error states, security                                       |
| Verify    | `cc-quality-practices`          | Judge whether tests actually cover this change, sized to the risk                          |

## The log

Maintain `IMPROVEMENT_LOG.md` at the repo root. It's the only memory this process has between cycles — treat it as
load-bearing, not optional bookkeeping. If it doesn't exist yet, this is cycle 1: create it with empty sections and
proceed.

```markdown
# Improvement Log

## Backlog

| Found | Location | Weakness | Principle | Severity | Status |
|---|---|---|---|---|---|
| 2026-08-23 | src/billing/Invoice.ts | applyDiscount has 6 nested conditionals | control flow | high | open |

## Cycle history

### 2026-08-23 — Cycle 14

- **Target:** Invoice.applyDiscount nesting
- **Why this one over others:** touched in ~40% of recent commits; every caller re-implements its own guard logic
- **Skills applied:** cc-control-flow-quality, cc-pseudocode-programming, cc-refactoring-guidance
- **Change:** extracted guard clauses, replaced nesting with early returns, no behavior change
- **Verification:** 38 existing tests green; added 4 for zero-quantity and expired-coupon edges
- **Docs touched:** docstring on applyDiscount
- **Deferred (new backlog rows):** discount-rule lookup leaks a raw Map across the module boundary
- **Next up:** that leak, or the OrderController boundary violation — roughly equal leverage
```

## The cycle

### 1. Orient

Read the log if it exists — you're resuming, not starting over. Consult `code-standards` to confirm (cycle 1: establish)
this codebase's real conventions: naming, module layout, test structure, error-handling idioms. Everything you write
later should look like it belongs here, not like a textbook example.

### 2. Diagnose

Mostly read, don't edit — the one exception is running a profiler, which is how `performance-optimization` finds
anything real; a hot path can't be diagnosed from reading code alone. Consult `aposd-reviewing-module-design`,
`cc-control-flow-quality`, `ca-architecture-boundaries`, `code-clarity-and-docs`, and, on any path where it plausibly
matters, `performance-optimization`, against whatever you haven't already logged. Cycle 1: a broad pass, weighted toward
the modules most other code depends on. Later cycles: re-scan what's changed since last time, plus one new area — you
don't need to re-audit everything every cycle.

For each weakness, write one backlog row: location, what's wrong, which principle it violates, and a severity based on
how much pain it causes today, not how it looks. Append to the backlog; don't fix anything yet, even the obvious ones.

### 3. Select one target

Pick exactly one backlog item. Prefer, roughly in this order:

- Weaknesses many callers already route around — a shallow or leaky module has more leverage than an ugly leaf function
- High-churn code over stable code nobody touches
- The smallest change that meaningfully closes the gap, over the theoretically complete redesign — leave room for next
  cycle
- Low-blast-radius fixes early on, while you're still building a track record of green cycles; save sweeping boundary
  work for once there's a test safety net you trust

If the target has little or no test coverage, the target for this cycle is writing characterization tests for its
current behavior, not restructuring it — you can't safely refactor what you can't verify. Consult `welc-legacy-code` for
the technique: finding seams, sprout/wrap methods, and breaking dependencies to get it under test without changing what
it does.

If the weakness itself is ambiguous — you can't tell what "fixed" would mean without a judgment call that isn't yours to
make — consult `clarify` to turn that into a specific, answerable question and log it as an open item rather than
picking an interpretation and running with it.

Log which item you picked, why, and what you passed over.

### 4. Design

Consult `cc-pseudocode-programming` and sketch the routine in pseudocode before writing real code; iterate there, not in
the implementation. For structural or shallow-interface problems, consult `aposd-designing-deep-modules` and
`aposd-simplifying-complexity` — the resulting interface should hide more than it exposes, and complexity should move
down into the module, not spread across its callers. Check `cc-routine-and-class-design` on the result: cohesion,
parameter count, one responsibility.

Reach for `gof-design-patterns` only if a specific pattern's forces genuinely match the symptom. A pattern that doesn't
remove a real structural problem is just indirection.

If this cycle's target came from `performance-optimization`, design the algorithmic or data-structure fix before
considering micro-optimizations, and decide now what you'll benchmark against — that commitment is what makes Verify
possible later.

If the fix would change a public interface or externally visible behavior, log that explicitly and flag it for human
review before implementing — that's the one call this process shouldn't make alone.

### 5. Implement

Consult `cc-refactoring-guidance`. If the target is wrong behavior, use `cc-debugging`'s
stabilize-locate-hypothesize-experiment cycle to find the actual cause before touching anything, fix it with the
smallest safe change, confirm it, then restructure separately — never mix a behavior change and a structural refactor in
one edit. If it's a pure design fix on code that already passes, behavior must not move at all. If you discover
mid-implementation that there's no real safety net for this code, stop restructuring and fall back to
`welc-legacy-code` — the same handoff `cc-refactoring-guidance` itself makes for untested legacy code.

Consult `cc-defensive-programming` for any boundary this change touches: validate at the edge, choose deliberately
between an assertion and a runtime exception, don't scatter redundant checks through the interior.

Keep the diff to the one target. Notice something else while you're in there? Log it as a new backlog row and leave it
alone.

### 6. Verify

Consult `aposd-verifying-correctness`: check the normal path, error and edge cases, and any security-relevant boundary
touched. Consult `cc-quality-practices` to judge whether existing tests actually exercise this change; add tests sized
to the risk, not padded for its own sake. If this cycle's target came from `performance-optimization`, verifying it
means running the benchmark you planned in Design — a change that "should be faster" but wasn't measured isn't verified.

Actually run the test suite and any linters or type-checkers the project uses. A cycle is verified because the commands
came back green, not because the code looks right. Anything fails → consult `cc-debugging` to find the actual cause
before deciding whether to fix it or revert, rather than patching the first symptom you see. Don't log a red cycle as
done.

### 7. Document and record

Consult `code-clarity-and-docs` for anything this change touches — comments, docstrings, READMEs, or architecture notes
that now describe the old shape. If this cycle changed or established a convention, update whatever `code-standards`
produced in step 1.

Update `IMPROVEMENT_LOG.md`: move the item into Cycle history with what changed, why, which skills you used, how you
verified it, what you deferred (as new backlog rows), and what looks like the natural next target.

If this repo is under git, commit the cycle on its own — one commit, message drawn from the log entry. Don't push,
merge, or open a PR unless asked.

### 8. Report

Summarize the cycle in a few sentences: what changed, why it was the highest-leverage choice available, and the
verification result. Then stop, unless you were explicitly asked to run in batch mode.

## Hard constraints

- One backlog item per cycle. If a fix needs three other things touched, shrink the target — don't widen the diff.
- Never combine a behavior change with a structural refactor in the same edit.
- Never mark a cycle done without actually running the tests.
- Never silently change public behavior — log it and flag it for review first.
- Don't add a pattern, defensive check, or abstraction layer that doesn't resolve a diagnosed symptom. This process
  removes complexity; it doesn't decorate code with more of it.
- If tests can't be made to pass, or the right fix needs a product decision, consult `clarify` to turn the ambiguity
  into concrete questions, then stop and escalate with those questions rather than guessing.

## Modes

**Interactive (default).** One cycle, then stop and report. Right for a normal session — someone reviews the diff before
the next cycle starts.

**Autonomous batch.** Only when explicitly requested ("run 5 cycles," "keep going until the boundary items are done").
The same verification gate applies to every cycle in the batch: the moment one doesn't go green, stop, leave the working
tree in the last known-good state, and report why. Don't push through a red cycle to hit a count.