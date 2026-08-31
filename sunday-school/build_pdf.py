#!/usr/bin/env python3
"""Build the Sunday School 'Marriage' lesson notes as a formatted PDF."""

from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.lib.enums import TA_JUSTIFY, TA_CENTER
from reportlab.lib import colors
from reportlab.platypus import (
    BaseDocTemplate, Frame, PageTemplate, Paragraph, Spacer,
    ListFlowable, ListItem, HRFlowable, KeepTogether,
)

OUT = "sunday-school-marriage.pdf"

INK = colors.HexColor("#1a1a1a")
ACCENT = colors.HexColor("#7a1f2b")
MUTED = colors.HexColor("#6b6b6b")
RULE = colors.HexColor("#c9b7a0")

title = ParagraphStyle(
    "title", fontName="Times-Bold", fontSize=30, leading=34,
    alignment=TA_CENTER, textColor=ACCENT, spaceAfter=4,
)
kicker = ParagraphStyle(
    "kicker", fontName="Helvetica", fontSize=10.5, leading=14,
    alignment=TA_CENTER, textColor=MUTED, spaceAfter=2,
    # letter spacing emulated with spaces in the text
)
subtitle = ParagraphStyle(
    "subtitle", fontName="Times-Italic", fontSize=12, leading=16,
    alignment=TA_CENTER, textColor=MUTED, spaceAfter=10,
)
h2 = ParagraphStyle(
    "h2", fontName="Helvetica-Bold", fontSize=13, leading=17,
    textColor=ACCENT, spaceBefore=16, spaceAfter=7,
)
h3 = ParagraphStyle(
    "h3", fontName="Helvetica-Bold", fontSize=10.5, leading=14,
    textColor=INK, spaceBefore=10, spaceAfter=5,
)
body = ParagraphStyle(
    "body", fontName="Times-Roman", fontSize=11.5, leading=16.5,
    alignment=TA_JUSTIFY, textColor=INK, spaceAfter=8,
)
bullet = ParagraphStyle(
    "bullet", parent=body, spaceAfter=6, leading=16,
    leftIndent=10 * mm, bulletIndent=4 * mm,
    bulletFontName="Times-Roman", bulletFontSize=11.5,
)
quote = ParagraphStyle(
    "quote", fontName="Times-Italic", fontSize=11.5, leading=17,
    leftIndent=16 * mm, rightIndent=10 * mm, textColor=ACCENT,
    spaceBefore=6, spaceAfter=10,
)
footer_style = ParagraphStyle(
    "footer", fontName="Helvetica", fontSize=8.5, leading=11,
    alignment=TA_CENTER, textColor=MUTED,
)


def P(text, style=body):
    return Paragraph(text, style)


def bullets(items):
    """Hanging-indent bullets with the marker on the first line's baseline."""
    dot = '<bullet color="#%s">&bull;</bullet>' % ACCENT.hexval()[2:]
    flow = [Spacer(1, 1)]
    for i in items:
        flow.append(Paragraph(dot + i, bullet))
    flow.append(Spacer(1, 3))
    return KeepTogether(flow) if len(items) <= 3 else flow


def rule():
    return HRFlowable(width="100%", thickness=0.7, color=RULE,
                      spaceBefore=10, spaceAfter=4)


story = []

# ---------------------------------------------------------------- cover block
story += [
    Spacer(1, 6 * mm),
    P("S U N D A Y &nbsp; S C H O O L", kicker),
    P("Marriage", title),
    HRFlowable(width="38%", thickness=1.2, color=RULE,
               spaceBefore=6, spaceAfter=6, hAlign="CENTER"),
    P("What God instituted in the beginning &mdash; a study in Genesis 1 and 2",
      subtitle),
    Spacer(1, 4 * mm),
]

# -------------------------------------------------------------- introduction
story += [
    P("Introduction: Going Back to Scripture", h2),
    P("There is a common saying that the man is supposed to be an "
      "authoritarian. Rather than argue from opinion, we should go to "
      "Scripture and see what God Himself describes."),
    P("When the Pharisees came to Jesus with their questions about marriage, "
      "He pointed them back to Genesis, to see what God originally instituted "
      "(Matthew 19:3&ndash;6)."),
    rule(),
]

# ------------------------------------------------------------------ section 1
story += [
    P("1. Humanity Created and Given a Mandate &mdash; Genesis 1:26&ndash;28", h2),
    P("Scripture says, <i>&ldquo;Let us make man in our image.&rdquo;</i> The "
      "passage later points to the fact that it was <b>male and female</b> "
      "that God actually planned to create."),
    bullets([
        "The woman is not a second derivative or an afterthought. It was man "
        "<b>and</b> woman that God intended to create from the beginning.",
        "God did not bless the man only; He blessed the man <b>and</b> the woman.",
        "He said to them, <i>&ldquo;Be fruitful and multiply, fill the earth "
        "and subdue it.&rdquo;</i>",
    ]),
    P("So when it comes to purpose &mdash; the topic of the man and the woman "
      "&mdash; Scripture is telling us about the female <b>and</b> the male, "
      "not just the man. You should not see the woman as second in authority "
      "or as a second derivative."),
    rule(),
]

# ------------------------------------------------------------------ section 2
story += [
    P("2. The Origin Story in Detail &mdash; Genesis 2:7&ndash;17", h2),
    P("Genesis 1 gives us the summary; Genesis 2 goes in depth into the same "
      "origin story."),
    bullets([
        "<b>Verse 7</b> &mdash; the LORD God <b>formed</b> the man from the "
        "dust of the ground.",
        "<b>Verse 15</b> &mdash; God <b>placed</b> the man in the garden to "
        "tend it and keep it.",
        "<b>Verses 16&ndash;17</b> &mdash; God gave the man a <b>commandment</b> "
        "and a <b>mandate</b> to follow.",
    ]),
    P("In Matthew 19 there was confusion about marriage, and Jesus&rsquo; "
      "answer was essentially, <i>&ldquo;Go back to Genesis and see how God "
      "intended it.&rdquo;</i> So do not feel lost or wonder why we are going "
      "through this process. Genesis shows us the pattern: the man was formed, "
      "the man was placed, and the man was given a commandment."),
    rule(),
]

# ------------------------------------------------------------------ section 3
story += [
    P("3. &ldquo;It Is Not Good for the Man to Be Alone&rdquo; &mdash; "
      "Genesis 2:18", h2),
    P("Read verse 18: <i>&ldquo;It is not good that man should be "
      "alone.&rdquo;</i>"),
    P("What does this mean? Is being alone a sin? Is it evil? Is it "
      "disgusting? At this point in the account, sin had not yet come into the "
      "world. So we should try to understand it by reading the previous verses."),
    P("Follow the refrain in Genesis 1:", h3),
    bullets([
        "<b>Verse 18</b> &mdash; the lights were made to rule over the day and "
        "the night, and to divide the light from the darkness, <i>&ldquo;and "
        "God saw that it was good.&rdquo;</i>",
        "<b>Verse 21</b> &mdash; <i>&ldquo;and God saw that it was good.&rdquo;</i>",
        "<b>Verse 25</b> &mdash; God made the beasts of the earth, the best of "
        "the earth, <i>&ldquo;and God saw that it was good.&rdquo;</i>",
    ]),
    P("Through the whole creation story, from day one to day six, God kept "
      "declaring, <i>&ldquo;It is good.&rdquo;</i> Then in Genesis "
      "1:26&ndash;27, God created man in His own image; in verse 28, He blessed "
      "them. And in Genesis 1:31, <i>&ldquo;God saw everything that He had "
      "made, and indeed it was very good.&rdquo;</i>"),
    P("So when God says in Genesis 2:18, <i>&ldquo;It is not good,&rdquo;</i> "
      "He is not saying that something is evil about the man, nor that "
      "everything else had suddenly become evil. What He means is that the man "
      "<b>alone</b> is functionally and structurally <b>incomplete</b> &mdash; "
      "in that state he is not able to fulfil the purpose God has given him."),
    P("Notice in Genesis 1:26&ndash;28 that God blessed <b>them</b> and said to "
      "<b>them</b> &mdash; them, them, them. God needed the pair for them to be "
      "fruitful, to multiply, and to subdue the earth."),
    rule(),
]

# ------------------------------------------------------------------ section 4
story += [
    P("4. Alone Is Not the Same as Lonely", h2),
    P("The man should not be alone &mdash; that is, he should not remain in a "
      "state of isolation. But we must not confuse <b>being alone</b> with "
      "<b>being lonely</b>."),
    P("Adam was not lonely. He had the presence of God and he had the animals. "
      "He knew no deficiency, no need, and no unmet desire. Loneliness is not "
      "the same thing as being alone."),
    P("It is not because you feel lonely that God determines you need a "
      "counterpart. It is when you have been <b>formed</b>, and when you are "
      "<b>following God&rsquo;s command</b>, that He says, <i>&ldquo;It is not "
      "good for you to be alone; you need someone else.&rdquo;</i>"),
    P("And when other people are pairing up around you, that in itself does "
      "not mean you need to marry. Scripture says it is as you are walking in "
      "God&rsquo;s purpose that you progress into the next thing."),
    rule(),
]

# ------------------------------------------------------------------ section 5
story += [
    P("5. Naming the Animals: God Creates the Desire &mdash; "
      "Genesis 2:19&ndash;20", h2),
    P("Adam knew no desire and no need. So God brought the creatures to him to "
      "name them, and that exercise <b>placed a desire in his heart</b>. As he "
      "saw the animals in their pairs, a desire was created in him &mdash; an "
      "awareness that he lacked something."),
    P("The sequence of the naming is an act of <b>cognitive classification and "
      "authority</b>. Adam, possessing immense intellect, defined the nature of "
      "every creature. He could say, <i>&ldquo;This kind of animal is not of my "
      "nature.&rdquo;</i> He had the ability to declare that this animal is, or "
      "is not, a counterpart to him. He must have seen every creature &mdash; "
      "even the chimpanzee, the nearest thing to him in appearance &mdash; and "
      "concluded that none of them was of his kind. That would have created in "
      "him a burning desire."),
    P("So in Genesis 2:18&ndash;22, it is not Adam&rsquo;s desire that makes "
      "him go searching for the woman. It is God who says, <i>&ldquo;I will "
      "make him a helper comparable to him,&rdquo;</i> and it is God who brings "
      "the woman to him afterwards."),
    P("When you see couples going about, it is not that desire which should "
      "push you to marry, but the fact that God has said you should not be "
      "alone."),
    P("Why the delay?", h3),
    P("You might wonder: was the naming unsuccessful? Was it only a device to "
      "make him desire? No &mdash; it was to make him feel his need and his "
      "limitation, so that he would know he could not do this for himself. "
      "Adam could have played with the animals; he could have enjoyed them. But "
      "when God finally did it for him, Adam was filled with <b>gratitude</b>. "
      "God showed him every creature so that he would feel the need, and so "
      "that when the woman was brought to him he would be filled with "
      "thanksgiving."),
    rule(),
]

# ------------------------------------------------------------------ section 6
story += [
    P("6. &ldquo;A Helper&rdquo; Does Not Mean &ldquo;A Subordinate&rdquo; "
      "&mdash; Genesis 2:18", h2),
    P("There is a saying that &ldquo;helper&rdquo; means subordinate, but that "
      "is wrong."),
    P("The Hebrew word used of the woman is <b>ezer</b>. The same word is used "
      "in Psalm 121:1&ndash;2: <i>&ldquo;I will lift up my eyes to the hills "
      "&mdash; from whence comes my help? My help comes from the LORD.&rdquo;</i> "
      "There, <b>ezer</b> describes God Himself, and it carries the sense of "
      "<b>indispensable, life-saving strength</b>, a <b>critical need met</b>, "
      "a <b>sustainer</b> of the person."),
    P("So it does not mean she is a subordinate. It means she is the "
      "indispensable, life-saving strength of the man. That is what it means to "
      "be <i>&ldquo;a helper comparable to him&rdquo;</i> &mdash; like a magnet, "
      "two opposite poles that belong together."),
    rule(),
]

# ------------------------------------------------------------------ section 7
story += [
    P("7. The Woman Brought to the Man &mdash; Genesis 2:21&ndash;24", h2),
    P("Adam did not go out to look for the woman; <b>she was brought to "
      "him</b>, so that the man could never claim he had a hand in her "
      "creation. And the rib was taken:"),
    KeepTogether(P(
        "The woman was not taken from the head to rule over the man,<br/>"
        "nor from the foot for the man to trample upon her,<br/>"
        "but out of his side, to be <b>equal</b> with him;<br/>"
        "under his arm, to be <b>protected</b> by him;<br/>"
        "near his heart, to be <b>loved</b> by him.", quote)),
    P("The woman is your strength, and someone you should love."),
    P("<b>Verse 23.</b> After the desire had been placed in the man &mdash; not "
      "by what other people were saying, but by God Himself &mdash; Adam saw "
      "the woman and said, <i>&ldquo;This is now bone of my bones and flesh of "
      "my flesh.&rdquo;</i> You can feel the relief in it: <i>finally, God has "
      "done it.</i> For all his superior intellect, he had classified every "
      "creature and found none like himself. At last he saw the woman and said, "
      "<i>&ldquo;This is the one.&rdquo;</i> The desire produced so much "
      "gratitude that he prophesied over her: this is co-man, a co-labourer in "
      "God&rsquo;s purpose."),
    P("<b>Verse 24.</b> <i>&ldquo;Therefore a man shall leave his father and "
      "mother and be joined to his wife, and they shall become one "
      "flesh.&rdquo;</i> This is God instituting marriage. The man was formed, "
      "and he was given a task. Marriage is not defined by what the world says, "
      "but by what Scripture has said."),
    rule(),
    Spacer(1, 3 * mm),
    P("<i>End of lesson notes.</i>", ParagraphStyle(
        "end", parent=body, alignment=TA_CENTER, textColor=MUTED,
        fontName="Times-Italic")),
]


def decorate(canvas, doc):
    canvas.saveState()
    w, h = A4
    # running head (not on page 1)
    if doc.page > 1:
        canvas.setFont("Helvetica", 8.5)
        canvas.setFillColor(MUTED)
        canvas.drawString(20 * mm, h - 13 * mm, "Sunday School  \u00b7  Marriage")
        canvas.setStrokeColor(RULE)
        canvas.setLineWidth(0.5)
        canvas.line(20 * mm, h - 15.5 * mm, w - 20 * mm, h - 15.5 * mm)
    # footer
    canvas.setFont("Helvetica", 8.5)
    canvas.setFillColor(MUTED)
    canvas.drawCentredString(w / 2, 12 * mm, str(doc.page))
    canvas.restoreState()


doc = BaseDocTemplate(
    OUT, pagesize=A4,
    leftMargin=20 * mm, rightMargin=20 * mm,
    topMargin=20 * mm, bottomMargin=20 * mm,
    title="Sunday School - Marriage",
    author="Sunday School Lesson Notes",
    subject="Marriage: what God instituted in the beginning (Genesis 1-2)",
)
frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="main")
doc.addPageTemplates([PageTemplate(id="all", frames=[frame], onPage=decorate)])
doc.build(story)
print("wrote", OUT)
