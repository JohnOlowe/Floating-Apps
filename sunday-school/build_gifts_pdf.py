#!/usr/bin/env python3
"""Build the Sunday School 'Concerning Spiritual Gifts' notes as a PDF."""

from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.lib.enums import TA_JUSTIFY, TA_CENTER
from reportlab.lib import colors
from reportlab.platypus import (
    BaseDocTemplate, Frame, PageTemplate, Paragraph, Spacer,
    HRFlowable, KeepTogether,
)

OUT = "spiritual-gifts.pdf"

INK = colors.HexColor("#1a1a1a")
ACCENT = colors.HexColor("#7a1f2b")
MUTED = colors.HexColor("#6b6b6b")
RULE = colors.HexColor("#c9b7a0")

title = ParagraphStyle("title", fontName="Times-Bold", fontSize=27, leading=32,
                       alignment=TA_CENTER, textColor=ACCENT, spaceAfter=4)
kicker = ParagraphStyle("kicker", fontName="Helvetica", fontSize=10.5, leading=14,
                        alignment=TA_CENTER, textColor=MUTED, spaceAfter=2)
subtitle = ParagraphStyle("subtitle", fontName="Times-Italic", fontSize=12, leading=16,
                          alignment=TA_CENTER, textColor=MUTED, spaceAfter=10)
h2 = ParagraphStyle("h2", fontName="Helvetica-Bold", fontSize=13, leading=17,
                    textColor=ACCENT, spaceBefore=16, spaceAfter=7)
h3 = ParagraphStyle("h3", fontName="Helvetica-Bold", fontSize=10.5, leading=14,
                    textColor=INK, spaceBefore=10, spaceAfter=5)
body = ParagraphStyle("body", fontName="Times-Roman", fontSize=11.5, leading=16.5,
                      alignment=TA_JUSTIFY, textColor=INK, spaceAfter=8)
bullet = ParagraphStyle("bullet", parent=body, spaceAfter=6, leading=16,
                        leftIndent=10 * mm, bulletIndent=4 * mm,
                        bulletFontName="Times-Roman", bulletFontSize=11.5)
quote = ParagraphStyle("quote", fontName="Times-Italic", fontSize=11.5, leading=17,
                       leftIndent=14 * mm, rightIndent=10 * mm, textColor=ACCENT,
                       spaceBefore=6, spaceAfter=10)


def P(t, s=body):
    return Paragraph(t, s)


def bullets(items):
    """Return ONE flowable holding the whole bullet group."""
    dot = '<bullet color="#%s">&bull;</bullet>' % ACCENT.hexval()[2:]
    flow = [Spacer(1, 1)] + [Paragraph(dot + i, bullet) for i in items] + [Spacer(1, 3)]
    return KeepTogether(flow)


def rule():
    return HRFlowable(width="100%", thickness=0.7, color=RULE,
                      spaceBefore=10, spaceAfter=4)


story = [
    Spacer(1, 6 * mm),
    P("S U N D A Y &nbsp; S C H O O L", kicker),
    P("Concerning Spiritual Gifts", title),
    HRFlowable(width="38%", thickness=1.2, color=RULE,
               spaceBefore=6, spaceAfter=6, hAlign="CENTER"),
    P("A study in 1 Corinthians 12", subtitle),
    Spacer(1, 3 * mm),
    P("<b>1 Corinthians 12:1</b> &mdash; <i>&ldquo;Now concerning spiritual "
      "gifts, brethren, I would not have you ignorant.&rdquo;</i>"),
    P("Concerning spiritual gifts, I should not be ignorant, I should not be "
      "misinformed, and I should not be lackadaisical."),
    rule(),

    P("1. What They Are, and Who Gives Them", h2),
    P("Spiritual gifts are a <b>supernatural, divine and spiritual "
      "endowment</b>."),
    bullets([
        "<b>The Giver</b> is God the Holy Spirit (1 Corinthians 12:4, 8, 11).",
        "<b>The receivers</b> are believers, the people who have the indwelling "
        "of the Spirit (1 Corinthians 12:2&ndash;3).",
    ]),
    P("It is important to know that <b>the indwelling comes before the "
      "gifts</b>. There are diversities of gifts, but one Giver, the same "
      "Spirit."),
    rule(),

    P("2. Why Are There Diversities of Gifts? &mdash; 1 Corinthians 12:12", h2),
    P("There are diversities of gifts, and all of them are given by the "
      "self-same Spirit. But there are no diversities of gifts for one person "
      "to be the custodian. We do not have diversities of gifts for the sake of "
      "one person."),
    bullets([
        "It is not necessary for one person to have all the gifts, and it is "
        "not for one person to monopolise the gifts of the Spirit.",
        "There are gifts to go round, gifts for every member of the church, to "
        "the end that the Church is <b>not lacking in any gift</b>.",
        "There should not be any member of the church who says the gifts are "
        "not for them.",
        "Our spiritual gifts are meant to <b>complement one another</b>, "
        "because they come from the same source.",
        "<b>No believer is exempted</b> from manifesting the gifts of the "
        "Spirit. And you do not need a platform to manifest some of them.",
    ]),
    rule(),

    P("3. Do Not Demean Your Gift &mdash; 1 Corinthians 12:15&ndash;16", h2),
    P("Some people will always be in front, like the eyes. But the ear must not "
      "say, <i>&ldquo;Because I am not the eye, I am not part of the "
      "body.&rdquo;</i>"),
    P("We do not demean our spiritual gifts."),
    rule(),

    P("4. Do Not Exalt Your Gift &mdash; 1 Corinthians 12:21", h2),
    P("The eye cannot say to the hand, <i>&ldquo;I have no need of you.&rdquo;</i>"),
    P("There is no chance for boasting, even with spiritual gifts. The end of "
      "the manifestation of spiritual gifts is that <b>God is glorified</b> "
      "(2 Corinthians 12:9&ndash;10)."),
    rule(),

    P("5. Unity and Love in the Exercise of the Gifts", h2),
    P("Unity should be maintained in the Church even with the expression of the "
      "diversities of gifts. There are diverse gifts, but those who carry them "
      "should not see themselves as set against each other."),
    P("All of the spiritual gifts should be <b>administered in love</b>. There "
      "is something it is compulsory for us to walk in, and that is love "
      "(1 Corinthians 13)."),
    P("Knowing God&rsquo;s character as portrayed in Scripture is the baseline "
      "for recognising His leadings through other means. <b>You cannot "
      "effectively minister the things of the Spirit if you do not understand "
      "the Scriptures.</b>"),
    rule(),

    P("6. Desire Them Earnestly", h2),
    P("The gifts of the Spirit should be an object of our desire and our "
      "pursuit. It should be something we aim for. We should <b>covet the gifts "
      "earnestly</b> (1 Corinthians 12:31; 14:1)."),
    KeepTogether(P("&ldquo;Spiritual gifts should be the object of our desires "
                   "and pursuits, all in subordination to the grace, the will "
                   "and the pleasure of God.&rdquo;", quote)),
    P("See also Romans 12:6 and 1 Corinthians 12:11, 18 &mdash; the Spirit "
      "distributes <i>&ldquo;to each one individually as He wills,&rdquo;</i> "
      "and God sets the members in the body <i>&ldquo;just as He "
      "pleased.&rdquo;</i>"),
    P("The end of our spiritual gifts is that <b>God is glorified and men are "
      "edified</b> (1 Corinthians 14:26)."),
    rule(),

    P("7. What Can Be Communicated Through the Gifts of the Spirit", h2),
    P("<b>Truths about God.</b> Insight into the person of God, and revelation "
      "of God, can be communicated through the gifts of the Spirit."),
    P("<b>Truths about individuals.</b> God can reveal things concerning people "
      "through the administration of the gifts, whether past, present or "
      "future."),
    P("All of the gifts of the Spirit do something, and all to the end that the "
      "recipients of the manifestation are <b>edified</b>."),
    P("Examples in Scripture", h3),
    bullets([
        "<b>Nathan the prophet</b> &mdash; 2 Samuel 12:1&ndash;15. He had a "
        "message to deliver to David, and it led the king to serious repentance.",
        "<b>Agabus</b> &mdash; Acts 11:27&ndash;28 and Acts 21:10&ndash;11. "
        "Known in the New Testament as a prophet; he foretold the famine, and "
        "later Paul&rsquo;s binding at Jerusalem.",
    ]),
    rule(),

    P("Closing", h2),
    P("The gifts of the Spirit are very important to every believer."),
    P("May God help us, that we would receive the gifts, and that we would be a "
      "blessing to others."),
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
                          "Sunday School  \u00b7  Concerning Spiritual Gifts")
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
    title="Sunday School - Concerning Spiritual Gifts",
    author="Sunday School Lesson Notes",
    subject="Concerning spiritual gifts (1 Corinthians 12)",
)
frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="main")
doc.addPageTemplates([PageTemplate(id="all", frames=[frame], onPage=decorate)])
doc.build(story)
print("wrote", OUT)
