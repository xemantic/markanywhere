# markanywhere-html-spec

Facts of the [HTML standard](https://html.spec.whatwg.org/) that more than one markanywhere module needs —
the parser, the HTML and Markdown renderers, and the HTML→Markdown pipeline —
kept in one place instead of a private copy in each.

It depends only on `markanywhere-api`,
so code that reads a semantic event stream carrying HTML attributes can use it without pulling in `markanywhere-html`.

| Declaration                                       | What it is                                                                                     |
|---------------------------------------------------|------------------------------------------------------------------------------------------------|
| `HTML_WHITESPACE_CHARS`, `Char.isHtmlWhitespace()` | HTML "ASCII whitespace": TAB, LF, FF, CR, SPACE — not NBSP, which HTML treats as content      |
| `String.isHtmlBlank()`                            | Empty or HTML whitespace only                                                                  |
| `HTML_VOID_ELEMENTS`                              | Elements with no content and no closing tag, including the obsolete `keygen` and `param`       |
| `HTML_RAW_TEXT_ELEMENTS`                          | `script` and `style`, whose content is neither escaped nor parsed as markup                    |
| `SemanticEvent.Mark.classList`                    | The distinct class names of a mark's `class` attribute, like the DOM's `classList`             |

Only facts of the standard belong here.
Policy — such as which elements a renderer lays out as blocks — stays with the module that makes the decision.
