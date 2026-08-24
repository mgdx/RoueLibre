# Glossaries

One file per translated language, listing the terms that language's
`strings.xml` holds to and the reason each one was picked. They exist so that
the same English word does not come out as three different ones over three
screens, and so that a contributor can correct one word later without unpicking
the whole file.

**An entry is not changed without going back over every one of its
occurrences.** That is the rule the whole set is built on: a glossary that
drifts from the file it describes is worse than no glossary, because the next
translator trusts it.

Each file is named by the ISO 639-1 code of its language, the same code as the
`app/src/main/res/values-<code>/` folder it describes.

## The languages

The register column is the one decision every glossary settles first, because
every string after it depends on it. It is not a house style: each language
follows what Android's own strings do in that language, which is why the answer
differs from one row to the next.

| Language | Glossary | Register |
|---|---|---|
| Albanian | [sq.md](sq.md) | second person singular |
| Arabic | [ar.md](ar.md) | Modern Standard Arabic, no T/V distinction to settle |
| Bosnian | [bs.md](bs.md) | *persiranje*, the polite plural |
| Croatian | [hr.md](hr.md) | *persiranje*, the polite plural |
| Czech | [cs.md](cs.md) | *vykání* — the reader is *vy* |
| Danish | [da.md](da.md) | *du*, the one address Danish has |
| Dutch | [nl.md](nl.md) | *je / jij* |
| Finnish | [fi.md](fi.md) | second person singular, imperative on buttons |
| French | [fr.md](fr.md) | *tu* (`SPEC.md` §9) |
| German | [de.md](de.md) | *du* |
| Greek | [el.md](el.md) | *εσείς*, the plural of politeness |
| Hungarian | [hu.md](hu.md) | nominal controls, *tegezés* in running prose |
| Italian | [it.md](it.md) | *tu*, never *Lei* |
| Japanese | [ja.md](ja.md) | plain *です・ます*, no commercial keigo |
| Latvian | [lv.md](lv.md) | infinitive on controls, polite plural in prose |
| Lithuanian | [lt.md](lt.md) | the polite plural, *jūs* |
| Norwegian Bokmål | [nb.md](nb.md) | *du*, the one address Norwegian has |
| Polish | [pl.md](pl.md) | informal, bare imperative, no *Pan/Pani* |
| Portuguese | [pt.md](pt.md) | third person singular, never *tu* |
| Romanian | [ro.md](ro.md) | *tu*, never the plural of politeness |
| Slovak | [sk.md](sk.md) | *vykanie* — the reader is *vy* |
| Slovenian | [sl.md](sl.md) | *vikanje*, bare imperative on buttons |
| Spanish | [es.md](es.md) | *tú*, never *usted* and never *vos* |
| Swedish | [sv.md](sv.md) | *du*, the one address Swedish has |
| Turkish | [tr.md](tr.md) | singular, *sen* |

## The languages still waiting

Five started files have no glossary yet, because they have no translation yet:
Basque (`eu`), Catalan (`ca`), Chinese (`zh`), Galician (`gl`) and Serbian
(`sr`). Their `strings.xml` still holds English text throughout. Whoever
translates one of them writes its glossary in the same breath — the register is
decided by the first screen translated, whether or not anybody writes it down,
and writing it down is what lets the next contributor agree with it.

English needs none: it is the source, in
[`app/src/main/res/values/strings.xml`](../../app/src/main/res/values/strings.xml).

## Using one

Before rewording anything in a translated `strings.xml`, read its glossary: the
term you are about to improve was probably picked against two alternatives, and
the file says which and why. If the reasoning no longer holds, change the entry
and every occurrence it covers, in the same commit.

[`CONTRIBUTING.md`](../../CONTRIBUTING.md) covers the rest of what a
translation involves — placeholders, plurals, declaring the language, and what
makes one finished.
