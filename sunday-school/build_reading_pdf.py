#!/usr/bin/env python3
"""Build the Sunday School 'Reading the Bible 2' notes as a PDF."""

from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.lib.enums import TA_JUSTIFY, TA_CENTER
from reportlab.lib import colors
from reportlab.platypus import (
    BaseDocTemplate, Frame, PageTemplate, Paragraph, Spacer,
    HRFlowable, KeepTogether,
)

OUT = "reading-the-bible-2.pdf"

INK = colors.HexColor("#1a1a1a")
ACCENT = colors.HexColor("#7a1f2b")
MUTED = colors.HexColor("#6b6b6b")
RULE = colors.HexColor("#c9b7a0")

title = ParagraphStyle("title", fontName="Times-Bold", fontSize=26, leading=31,
                       alignment=TA_CENTER, textColor=ACCENT, spaceAfter=4)
kicker = ParagraphStyle("kicker", fontName="Helvetica", fontSize=10.5, leading=14,
                        alignment=TA_CENTER, textColor=MUTED, spaceAfter=2)
subtitle = ParagraphStyle("subtitle", fontName="Times-Italic", fontSize=11.5, leading=16,
                          alignment=TA_CENTER, textColor=MUTED, spaceAfter=10)
h2 = ParagraphStyle("h2", fontName="Helvetica-Bold", fontSize=13, leading=17,
                    textColor=ACCENT, spaceBefore=16, spaceAfter=7)
h3 = ParagraphStyle("h3", fontName="Helvetica-Bold", fontSize=10.5, leading=14,
                    textColor=INK, spaceBefore=10, spaceAfter=5)
body = ParagraphStyle("body", fontName="Times-Roman", fontSize=11.2, leading=16.2,
                      alignment=TA_JUSTIFY, textColor=INK, spaceAfter=8)
bullet = ParagraphStyle("bullet", parent=body, spaceAfter=6, leading=16,
                        leftIndent=10 * mm, bulletIndent=4 * mm,
                        bulletFontName="Times-Roman", bulletFontSize=11.2)
quote = ParagraphStyle("quote", fontName="Times-Italic", fontSize=11.2, leading=16.5,
                       leftIndent=14 * mm, rightIndent=10 * mm, textColor=ACCENT,
                       spaceBefore=6, spaceAfter=10)


def P(t, s=body):
    return Paragraph(t, s)


def bullets(items):
    dot = '<bullet color="#%s">&bull;</bullet>' % ACCENT.hexval()[2:]
    flow = [Spacer(1, 1)] + [Paragraph(dot + i, bullet) for i in items] + [Spacer(1, 3)]
    return KeepTogether(flow)


def rule():
    return HRFlowable(width="100%", thickness=0.7, color=RULE,
                      spaceBefore=10, spaceAfter=4)


story = [
    Spacer(1, 6 * mm),
    P("S U N D A Y &nbsp; S C H O O L", kicker),
    P("Reading the Bible 2", title),
    HRFlowable(width="38%", thickness=1.2, color=RULE,
               spaceBefore=6, spaceAfter=6, hAlign="CENTER"),
    P("Bible Hermeneutics and Translations &mdash; how to read genres rightly", subtitle),
    Spacer(1, 3 * mm),
    P("Every book of the Bible was written with a purpose, an audience and a literary form. Reading it well means honouring how it was written."),
    rule(),

    P("1. Wisdom Literature &mdash; Proverbs, Ecclesiastes, Job", h2),
    P("<b>Proverbs</b> gives us wisdom for everyday living. We can apply it."),
    P("<b>Ecclesiastes</b> takes us through the frustrations of someone living in a broken world. When we read it, we should allow our minds to feel those frustrations."),
    P("<b>Job</b> shows us that this world is very sinful and less than ideal. We should not read those verses and keep our minds expecting only promises, because this world is not ideal. God is sovereign even when life is hard."),
    KeepTogether(P("<i>Read wisdom literature for what it is. Proverbs for principles to live by, Ecclesiastes and Job to feel the weight of life in a fallen world.</i>", quote)),
    rule(),

    P("2. Poetry and Songs &mdash; Psalms, Lamentations, Song of Solomon", h2),
    P("These books are filled with artistic writings full of raw human emotion, imagery and rhythm."),
    bullets([
        "Like modern poetry, biblical poetry uses a lot of metaphor and images.",
        "We should remember we are reading poems, and we should not read them like prose, but like poetry.",
        "These books express their feelings to God. We see joy, hopelessness, danger.",
        "We see how the psalmist shows different emotions and different states. They are expressions of feeling to God, and they can serve as a guide for our prayers and worship.",
        "Even at the end of everything, the psalmist is someone who honours God.",
    ]),
    rule(),

    P("3. Prophecies &mdash; Isaiah, Jeremiah, Zechariah, Amos, etc.", h2),
    P("They are messages calling ancient people back to faithfulness to God, alongside warnings of judgement and future hope."),
    P("When we read the prophets, we should keep in mind that this Scripture was written by a prophet to God&rsquo;s own people at the time. The immediate audience was the people of Israel in that generation, before it speaks to us."),
    rule(),

    P("4. The Gospels &mdash; Matthew, Mark, Luke and John", h2),
    P("The Gospels contain a unique <b>biographical</b> portrait of the life, death and resurrection of Jesus."),
    bullets([
        "You read them as testimonies written from unique perspectives to highlight the person of Christ.",
        "In all their writings, they did not write the same way. They wrote about the same Christ, even though they were unique in their approach. Their accounts are complementary, not contradictory.",
        "We should pay attention to the parables Jesus told. These are earthly stories with a heavenly or spiritual meaning.",
    ]),
    rule(),

    P("5. The Epistles &mdash; The Letters to the Churches", h2),
    P("The Epistles are the letters of the apostles to the churches at the time."),
    bullets([
        "When reading the Epistles, pay attention to the logic the author is trying to establish.",
        "It pays to read the whole letter at a go, so that you understand the flow of thought.",
        "Look out for literary markers like &ldquo;if&rdquo;, &ldquo;then&rdquo;, &ldquo;but&rdquo; and &ldquo;therefore&rdquo;. To understand what those parts are saying, you often need to go back to the previous chapter.",
        "Use a study Bible or commentaries.",
        "Research the author and the circumstances under which he wrote the letter, and the recipients of the letter.",
    ]),
    rule(),

    P("6. The Apocalypse &mdash; Revelation (and Daniel)", h2),
    P("When reading the Book of Revelation, do not overliteralize the apocalyptic visions."),
    bullets([
        "The apocalyptic literature of Revelation and Daniel was not written so that we can work out when Christ will come.",
        "The Book of Revelation was not written to scare believers, but to encourage and challenge persecuted believers in their journey of faith.",
        "The picture it paints is that <b>God ultimately wins</b>.",
        "It also shows us about the renewal of creation. It is meant to encourage us, not to scare us.",
    ]),
    rule(),

    P("7. Bible Translations", h2),
    P("<b>What is translation?</b> Translation means to restate the meaning of words in one language with words from another language. Translation is very important, but however the Bible is being translated, it should be translated correctly."),
    P("<b>Original languages:</b>", h3),
    bullets([
        "The Old Testament was written in <b>Hebrew</b>, except for some parts of Ezra and Daniel that were written in <b>Aramaic</b>.",
        "The New Testament was written in <b>Greek</b>.",
    ]),
    P("Two main approaches, plus paraphrases", h3),
    P("<b>1. Word-for-Word / Formal Equivalence</b> &mdash; This is the strictest approach. These translations attempt to translate from the original language to the new one, word for word and grammar for grammar. They represent the closest you will get to the original language. They were written very strictly, with no addition. The trade-off is that they can be difficult to read at times.", body),
    bullets([
        "<b>NASB</b> &mdash; New American Standard Bible",
        "<b>KJV</b> &mdash; King James Version",
        "<b>ESV</b> &mdash; English Standard Version",
    ]),
    P("<b>2. Thought-for-Thought / Functional / Dynamic Equivalence</b> &mdash; These focus more on readability. They attempt to understand the meaning of the text, and they try to translate it in a way that modern readers will understand. They translate based on context, not word for word. Strength: easier to read. Weakness: they might miss what the author is trying to convey, including intentional repetitions.", body),
    bullets([
        "<b>NIV</b> &mdash; New International Version (not &ldquo;Translation&rdquo;)",
        "<b>NLT</b> &mdash; New Living Translation",
    ]),
    P("<b>3. Paraphrased</b> &mdash; There are translations that paraphrase what is written. They retell the text using modern everyday speech, but they drift very far away from the wording of Scripture. You cannot really use a paraphrase as your main Bible for study.", body),
    P("Translations to avoid or use with great caution", h3),
    bullets([
        "<b>New World Translation (NWT)</b> &mdash; Altered to fit a particular sect (Jehovah&rsquo;s Witnesses). It twists the true Word of the Bible and denies the deity and Trinity of Christ.",
        "<b>The Passion Translation (TPT)</b> &mdash; Single-handedly translated by one man. It weakens doctrine a lot and adds too much to the Scripture.",
        "<b>The Message Bible</b> &mdash; Should not be used for Bible study. It blurs the line between the author&rsquo;s meaning and the translator&rsquo;s modern interpretation.",
    ]),
    P("<b>Which one is best?</b> There is not a single best translation. They can be studied side by side. Use a formal translation as your main study Bible, and compare with a functional translation for clarity."),
    rule(),

    P("Closing", h2),
    P("May the Lord use all we have learnt about Bible translations and genres to help our hearts. May God help us to apply them, and to yield to the corrections of Scripture. May we study God&rsquo;s Word rightly and understand God&rsquo;s Word correctly."),
    rule(),
    Spacer(1, 3 * mm),
    P("<i>End of lesson notes.</i>", ParagraphStyle(
        "end", parent=body, alignment=TA_CENTER, textColor=MUTED,
        fontName="Times-Italic")),
]


def decorate(canvas, doc):
    canvas.saveState()
    w, h = A4
    if doc.page > 1:
        canvas.setFont("Helvetica", 8.5)
        canvas.setFillColor(MUTED)
        canvas.drawString(20 * mm, h - 13 * mm,
                          "Sunday School  \u00b7  Reading the Bible 2")
        canvas.setStrokeColor(RULE)
        canvas.setLineWidth(0.5)
        canvas.line(20 * mm, h - 15.5 * mm, w - 20 * mm, h - 15.5 * mm)
    canvas.setFont("Helvetica", 8.5)
    canvas.setFillColor(MUTED)
    canvas.drawCentredString(w / 2, 12 * mm, str(doc.page))
    canvas.restoreState()


doc = BaseDocTemplate(
    OUT, pagesize=A4,
    leftMargin=20 * mm, rightMargin=20 * mm,
    topMargin=20 * mm, bottomMargin=20 * mm,
    title="Sunday School - Reading the Bible 2",
    author="Sunday School Lesson Notes",
    subject="Bible Hermeneutics and Translations",
)
frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="main")
doc.addPageTemplates([PageTemplate(id="all", frames=[frame], onPage=decorate)])
doc.build(story)
print("wrote", OUT)
