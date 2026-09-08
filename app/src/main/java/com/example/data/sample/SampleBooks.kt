package com.example.data.sample

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import com.example.data.model.Book
import com.example.data.parser.PdfBookParser
import com.example.data.parser.ParsedBook
import com.example.data.parser.SpineChapter
import com.example.data.parser.TocItem
import java.io.File
import java.io.FileOutputStream

object SampleBooks {

    fun createSampleCover(title: String, author: String, bgColor: Int, accentColor: Int): Bitmap {
        val width = 360
        val height = 520
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Border frame
        paint.color = accentColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        val rect = RectF(20f, 20f, width - 20f, height - 20f)
        canvas.drawRoundRect(rect, 12f, 12f, paint)

        // Inner decorative line
        paint.strokeWidth = 2f
        val innerRect = RectF(30f, 30f, width - 30f, height - 30f)
        canvas.drawRoundRect(innerRect, 8f, 8f, paint)

        // Title
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.isFakeBoldText = true

        val words = title.split(" ")
        var y = 140f
        var currentLine = ""
        for (word in words) {
            if (paint.measureText("$currentLine $word".trim()) < width - 80) {
                currentLine = "$currentLine $word".trim()
            } else {
                canvas.drawText(currentLine, 45f, y, paint)
                y += 36f
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) {
            canvas.drawText(currentLine, 45f, y, paint)
        }

        // Accent divider
        paint.color = accentColor
        paint.strokeWidth = 4f
        y += 30f
        canvas.drawLine(45f, y, width - 45f, y, paint)

        // Author
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#E0E0E0")
        paint.textSize = 20f
        paint.isFakeBoldText = false
        canvas.drawText("by $author", 45f, y + 40f, paint)

        // Badge at bottom
        paint.color = accentColor
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("LUMINA CLASSICS", 45f, height - 50f, paint)

        return bitmap
    }

    fun getSherlockHolmes(): ParsedBook {
        val chapters = listOf(
            SpineChapter(
                id = "sh_ch_1",
                title = "A Scandal in Bohemia",
                plainText = """To Sherlock Holmes she is always THE woman. I have seldom heard him mention her under any other name. In his eyes she eclipses and predominates the whole of her sex. It was not that he felt any emotion akin to love for Irene Adler. All emotions, and that one particularly, were abhorrent to his cold, precise but admirably balanced mind. He was, I take it, the most perfect reasoning and observing machine that the world has seen, but as a lover he would have placed himself in a false position.

He never spoke of the softer passions, save with a gibe and a sneer. They were admirable things for the observer—excellent for drawing the veil from men’s motives and actions. But for the trained reasoner to admit such intrusions into his own delicate and finely adjusted temperament was to introduce a distracting factor which might throw a doubt upon all his mental results.

Grit in a sensitive instrument, or a crack in one of his own high-power lenses, would not be more disturbing than a strong emotion in a nature such as his. And yet there was but one woman to him, and that woman was the late Irene Adler, of dubious and questionable memory.

One night—it was on the twentieth of March, 1888—I was returning from a journey to a patient (for I had now returned to civil practice), when my way led me through Baker Street. As I passed the well-remembered door, which must always be associated in my mind with my wooing, and with the dark incidents of the Study in Scarlet, I was seized with a keen desire to see Holmes again, and to know how he was employing his extraordinary powers.

His rooms were brilliantly lit, and, even as I looked up, I saw his tall, spare figure pass twice in a dark silhouette against the blind. He was pacing the room swiftly, eagerly, with his head sunk upon his chest and his hands clasped behind him. To me, who knew his every mood and habit, his attitude and manner told their own story. He was at work again. He had risen out of his drug-created dreams and was hot upon the scent of some new problem. I rang the bell and was shown up to the chamber which had formerly been in part my own.""",
                formattedParagraphs = listOf(
                    "To Sherlock Holmes she is always THE woman. I have seldom heard him mention her under any other name. In his eyes she eclipses and predominates the whole of her sex. It was not that he felt any emotion akin to love for Irene Adler. All emotions, and that one particularly, were abhorrent to his cold, precise but admirably balanced mind.",
                    "He never spoke of the softer passions, save with a gibe and a sneer. They were admirable things for the observer—excellent for drawing the veil from men’s motives and actions. But for the trained reasoner to admit such intrusions into his own delicate and finely adjusted temperament was to introduce a distracting factor which might throw a doubt upon all his mental results.",
                    "Grit in a sensitive instrument, or a crack in one of his own high-power lenses, would not be more disturbing than a strong emotion in a nature such as his. And yet there was but one woman to him, and that woman was the late Irene Adler, of dubious and questionable memory.",
                    "One night—it was on the twentieth of March, 1888—I was returning from a journey to a patient, when my way led me through Baker Street. As I passed the well-remembered door, I was seized with a keen desire to see Holmes again, and to know how he was employing his extraordinary powers.",
                    "His rooms were brilliantly lit, and, even as I looked up, I saw his tall, spare figure pass twice in a dark silhouette against the blind. He was pacing the room swiftly, eagerly, with his head sunk upon his chest and his hands clasped behind him. He had risen out of his dreams and was hot upon the scent of some new problem."
                ),
                wordCount = 380
            ),
            SpineChapter(
                id = "sh_ch_2",
                title = "The Red-Headed League",
                plainText = """I had called upon my friend, Mr. Sherlock Holmes, one day in the autumn of last year and found him in deep conversation with a very stout, florid-faced, elderly gentleman with fiery red hair. With an apology for my intrusion, I was about to withdraw when Holmes pulled me abruptly into the room and closed the door behind me.

“You could not have come at a better time, my dear Watson,” he said cordially.

“I was afraid that you were engaged.”

“So I am. Very much so.”

“Then I can wait in the next room.”

“Not at all. This gentleman, Mr. Wilson, has been my partner and helper in many of my most successful cases, and I have no doubt that he will be of the utmost use to me in yours also.”

The stout gentleman half rose from his chair and gave a bob of greeting, with a quick little questioning glance from his small fat-encircled eyes.

“Try the settee,” said Holmes, relapsing into his armchair and putting his fingertips together, as was his custom when in judicial moods. “I know, my dear Watson, that you share my love of all that is bizarre and outside the conventions and humdrum routine of everyday life.”""",
                formattedParagraphs = listOf(
                    "I had called upon my friend, Mr. Sherlock Holmes, one day in the autumn of last year and found him in deep conversation with a very stout, florid-faced, elderly gentleman with fiery red hair.",
                    "“You could not have come at a better time, my dear Watson,” he said cordially. “This gentleman, Mr. Wilson, has been my partner and helper in many of my most successful cases, and I have no doubt that he will be of the utmost use to me in yours also.”",
                    "The stout gentleman half rose from his chair and gave a bob of greeting, with a quick little questioning glance from his small fat-encircled eyes.",
                    "“Try the settee,” said Holmes, relapsing into his armchair and putting his fingertips together, as was his custom when in judicial moods. “I know, my dear Watson, that you share my love of all that is bizarre and outside the conventions and humdrum routine of everyday life.”"
                ),
                wordCount = 210
            ),
            SpineChapter(
                id = "sh_ch_3",
                title = "A Case of Identity",
                plainText = """“My dear fellow,” said Sherlock Holmes as we sat on either side of the fire in his lodgings at Baker Street, “life is infinitely stranger than anything which the mind of man could invent. We would not dare to conceive the things which are really mere commonplaces of existence. If we could fly out of that window hand in hand, hover over this great city, gently remove the roofs, and peep in at the queer things which are going on, the strange coincidences, the plannings, the cross-purposes, the wonderful chains of events, working through generations, and leading to the most outre results, it would make all fiction with its conventionalities and foreseen conclusions most stale and unprofitable.”

“And yet I am not convinced of it,” I answered. “The cases which come to light in the papers are, as a rule, bald enough, and vulgar enough. We have in our police reports realism pushed to its extreme limits, and yet the result is, it must be confessed, neither fascinating nor artistic.”

“A certain selection and discretion must be used in producing a realistic effect,” remarked Holmes. “This is wanting in the police report, where more stress is laid, perhaps, upon the platitudes of the magistrate than upon the details, which to an observer contain the vital essence of the whole matter.”""",
                formattedParagraphs = listOf(
                    "“My dear fellow,” said Sherlock Holmes as we sat on either side of the fire in his lodgings at Baker Street, “life is infinitely stranger than anything which the mind of man could invent.”",
                    "“If we could fly out of that window hand in hand, hover over this great city, gently remove the roofs, and peep in at the queer things which are going on, it would make all fiction with its conventionalities and foreseen conclusions most stale and unprofitable.”",
                    "“And yet I am not convinced of it,” I answered. “The cases which come to light in the papers are, as a rule, bald enough, and vulgar enough.”",
                    "“A certain selection and discretion must be used in producing a realistic effect,” remarked Holmes. “This is wanting in the police report, where more stress is laid upon the platitudes than upon the details, which contain the vital essence of the whole matter.”"
                ),
                wordCount = 240
            )
        )

        return ParsedBook(
            title = "The Adventures of Sherlock Holmes",
            author = "Arthur Conan Doyle",
            format = "EPUB",
            tableOfContents = listOf(
                TocItem("sh_ch_1", "I. A Scandal in Bohemia", 0),
                TocItem("sh_ch_2", "II. The Red-Headed League", 1),
                TocItem("sh_ch_3", "III. A Case of Identity", 2)
            ),
            chapters = chapters,
            totalPagesEstimate = 120
        )
    }

    fun getAliceInWonderland(): ParsedBook {
        val chapters = listOf(
            SpineChapter(
                id = "alice_ch_1",
                title = "Down the Rabbit-Hole",
                plainText = """Alice was beginning to get very tired of sitting by her sister on the bank, and of having nothing to do: once or twice she had peeped into the book her sister was reading, but it had no pictures or conversations in it, “and what is the use of a book,” thought Alice “without pictures or conversations?”

So she was considering in her own mind (as well as she could, for the hot day made her feel very sleepy and stupid), whether the pleasure of making a daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly a White Rabbit with pink eyes ran close by her.

There was nothing so very remarkable in that; nor did Alice think it so very much out of the way to hear the Rabbit say to itself, “Oh dear! Oh dear! I shall be late!” (when she thought it over afterwards, it occurred to her that she ought to have wondered at this, but at the time it all seemed quite natural); but when the Rabbit actually took a watch out of its waistcoat-pocket, and looked at it, and then hurried on, Alice started to her feet, for it flashed across her mind that she had never before seen a rabbit with either a waistcoat-pocket, or a watch to take out of it, and burning with curiosity, she ran across the field after it, and fortunately was just in time to see it pop down a large rabbit-hole under the hedge.

In another moment down went Alice after it, never once considering how in the world she was to get out again.""",
                formattedParagraphs = listOf(
                    "Alice was beginning to get very tired of sitting by her sister on the bank, and of having nothing to do: once or twice she had peeped into the book her sister was reading, but it had no pictures or conversations in it, “and what is the use of a book,” thought Alice “without pictures or conversations?”",
                    "So she was considering in her own mind whether the pleasure of making a daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly a White Rabbit with pink eyes ran close by her.",
                    "There was nothing so very remarkable in that; nor did Alice think it so very much out of the way to hear the Rabbit say to itself, “Oh dear! Oh dear! I shall be late!”",
                    "In another moment down went Alice after it, never once considering how in the world she was to get out again."
                ),
                wordCount = 280
            ),
            SpineChapter(
                id = "alice_ch_2",
                title = "The Pool of Tears",
                plainText = """“Curiouser and curiouser!” cried Alice (she was so much surprised, that for the moment she quite forgot how to speak good English); “now I’m opening out like the largest telescope that ever was! Good-bye, feet!” (for when she looked down at her feet, they seemed to be almost out of sight, they were getting so far off). “Oh, my poor little feet, I wonder who will put on your shoes and stockings for you now, dears? I’m sure I shan’t be able! I shall be a great deal too far off to trouble myself about you: you must manage the best way you can;—but I must be kind to them,” thought Alice, “or perhaps they won’t walk the way I want to go! Let me see: I’ll give them a new pair of boots every Christmas.”

And she went on planning to herself how she would manage it. “They must go by the carrier,” she thought; “and how funny it’ll seem, sending presents to one’s own feet! And how odd the directions will look!

Alice’s Right Foot, Esq.
Hearthrug,
near the Fender,
(with Alice’s love).
Oh dear, what nonsense I’m talking!”""",
                formattedParagraphs = listOf(
                    "“Curiouser and curiouser!” cried Alice (she was so much surprised, that for the moment she quite forgot how to speak good English); “now I’m opening out like the largest telescope that ever was! Good-bye, feet!”",
                    "“Oh, my poor little feet, I wonder who will put on your shoes and stockings for you now, dears? I’m sure I shan’t be able! I shall be a great deal too far off to trouble myself about you.”",
                    "And she went on planning to herself how she would manage it. “They must go by the carrier,” she thought; “and how funny it’ll seem, sending presents to one’s own feet!”"
                ),
                wordCount = 210
            )
        )

        return ParsedBook(
            title = "Alice's Adventures in Wonderland",
            author = "Lewis Carroll",
            format = "EPUB",
            tableOfContents = listOf(
                TocItem("alice_ch_1", "I. Down the Rabbit-Hole", 0),
                TocItem("alice_ch_2", "II. The Pool of Tears", 1)
            ),
            chapters = chapters,
            totalPagesEstimate = 96
        )
    }

    fun getFrankenstein(): ParsedBook {
        val chapters = listOf(
            SpineChapter(
                id = "frank_letter_1",
                title = "Letter 1",
                plainText = """To Mrs. Saville, England.
St. Petersburgh, Dec. 11th, 17—.

You will rejoice to hear that no disaster has accompanied the commencement of an enterprise which you have regarded with such evil forebodings. I arrived here yesterday, and my first task is to assure my dear sister of my welfare and increasing confidence in the success of my undertaking.

I am already far north of London, and as I walk in the streets of Petersburgh, I feel a cold northern breeze play upon my cheeks, which braces my nerves and fills me with delight. Do you understand this feeling? This breeze, which has travelled from the regions towards which I am advancing, gives me a foretaste of those icy climes. Inspirited by this wind of promise, my daydreams become more fervent and vivid. I try in vain to be persuaded that the pole is the seat of frost and desolation; it ever presents itself to my imagination as the region of beauty and delight.""",
                formattedParagraphs = listOf(
                    "To Mrs. Saville, England.\nSt. Petersburgh, Dec. 11th, 17—.",
                    "You will rejoice to hear that no disaster has accompanied the commencement of an enterprise which you have regarded with such evil forebodings. I arrived here yesterday, and my first task is to assure my dear sister of my welfare and increasing confidence in the success of my undertaking.",
                    "I am already far north of London, and as I walk in the streets of Petersburgh, I feel a cold northern breeze play upon my cheeks, which braces my nerves and fills me with delight. Do you understand this feeling?",
                    "I try in vain to be persuaded that the pole is the seat of frost and desolation; it ever presents itself to my imagination as the region of beauty and delight."
                ),
                wordCount = 180
            ),
            SpineChapter(
                id = "frank_ch_1",
                title = "Chapter 1",
                plainText = """I am by birth a Genevese, and my family is one of the most distinguished of that republic. My ancestors had been for many years counsellors and syndics, and my father had filled several public situations with honour and reputation. He was respected by all who knew him for his integrity and indefatigable attention to public business. He passed his younger days perpetually occupied by the affairs of his country; nor was it until the decline of life that he became a husband and the father of a family.

As the circumstances of his marriage illustrate his character, I cannot refrain from relating them. One of his most intimate friends was a merchant who, from a flourishing state, fell, through numerous mischances, into poverty. This man, whose name was Beaufort, was of a proud and unbending disposition and could not bear to live in poverty and oblivion in the same country where he had once been distinguished for his rank and magnificence.""",
                formattedParagraphs = listOf(
                    "I am by birth a Genevese, and my family is one of the most distinguished of that republic. My ancestors had been for many years counsellors and syndics, and my father had filled several public situations with honour and reputation.",
                    "As the circumstances of his marriage illustrate his character, I cannot refrain from relating them. One of his most intimate friends was a merchant who, from a flourishing state, fell into poverty. This man was of a proud and unbending disposition and could not bear to live in poverty."
                ),
                wordCount = 160
            )
        )

        return ParsedBook(
            title = "Frankenstein",
            author = "Mary Wollstonecraft Shelley",
            format = "TXT",
            tableOfContents = listOf(
                TocItem("frank_letter_1", "Letter 1", 0),
                TocItem("frank_ch_1", "Chapter 1", 1)
            ),
            chapters = chapters,
            totalPagesEstimate = 140
        )
    }

    /**
     * Seeds initial books on first run if database is empty.
     */
    fun seedDefaultBooks(context: Context): List<Pair<Book, ParsedBook>> {
        val booksDir = File(context.filesDir, "sample_books")
        if (!booksDir.exists()) booksDir.mkdirs()

        val coversDir = File(context.filesDir, "covers")
        if (!coversDir.exists()) coversDir.mkdirs()

        val list = mutableListOf<Pair<Book, ParsedBook>>()

        // 1. Sherlock Holmes
        val shParsed = getSherlockHolmes()
        val shCover = createSampleCover(shParsed.title, shParsed.author, Color.parseColor("#1B2A4A"), Color.parseColor("#F59E0B"))
        val shCoverFile = File(coversDir, "cover_sherlock.png")
        FileOutputStream(shCoverFile).use { shCover.compress(Bitmap.CompressFormat.PNG, 95, it) }

        val shFile = File(booksDir, "sherlock_holmes.epub")
        if (!shFile.exists()) shFile.writeText("Sample EPUB Content")

        val shBook = Book(
            id = 1,
            filePath = shFile.absolutePath,
            title = shParsed.title,
            author = shParsed.author,
            format = "EPUB",
            coverPath = shCoverFile.absolutePath,
            dateAdded = System.currentTimeMillis() - 86400000 * 3,
            lastOpened = System.currentTimeMillis() - 3600000 * 2,
            progressPercent = 14.5f,
            progressLocation = "0",
            currentChapterTitle = "A Scandal in Bohemia",
            readingStatus = "READING",
            isFavorite = true,
            fileHash = "sample_hash_sh_1",
            totalPages = 120,
            fileSizeBytes = 450000
        )
        list.add(Pair(shBook, shParsed))

        // 2. Alice in Wonderland
        val aliceParsed = getAliceInWonderland()
        val aliceCover = createSampleCover(aliceParsed.title, aliceParsed.author, Color.parseColor("#4C1D95"), Color.parseColor("#38BDF8"))
        val aliceCoverFile = File(coversDir, "cover_alice.png")
        FileOutputStream(aliceCoverFile).use { aliceCover.compress(Bitmap.CompressFormat.PNG, 95, it) }

        val aliceFile = File(booksDir, "alice_wonderland.epub")
        if (!aliceFile.exists()) aliceFile.writeText("Sample EPUB Content")

        val aliceBook = Book(
            id = 2,
            filePath = aliceFile.absolutePath,
            title = aliceParsed.title,
            author = aliceParsed.author,
            format = "EPUB",
            coverPath = aliceCoverFile.absolutePath,
            dateAdded = System.currentTimeMillis() - 86400000 * 5,
            lastOpened = System.currentTimeMillis() - 86400000,
            progressPercent = 45.0f,
            progressLocation = "1",
            currentChapterTitle = "The Pool of Tears",
            readingStatus = "READING",
            isFavorite = false,
            fileHash = "sample_hash_alice_2",
            totalPages = 96,
            fileSizeBytes = 320000
        )
        list.add(Pair(aliceBook, aliceParsed))

        // 3. Frankenstein
        val frankParsed = getFrankenstein()
        val frankCover = createSampleCover(frankParsed.title, frankParsed.author, Color.parseColor("#064E3B"), Color.parseColor("#10B981"))
        val frankCoverFile = File(coversDir, "cover_frankenstein.png")
        FileOutputStream(frankCoverFile).use { frankCover.compress(Bitmap.CompressFormat.PNG, 95, it) }

        val frankFile = File(booksDir, "frankenstein.txt")
        if (!frankFile.exists()) frankFile.writeText("Sample TXT Content")

        val frankBook = Book(
            id = 3,
            filePath = frankFile.absolutePath,
            title = frankParsed.title,
            author = frankParsed.author,
            format = "TXT",
            coverPath = frankCoverFile.absolutePath,
            dateAdded = System.currentTimeMillis() - 86400000 * 7,
            lastOpened = 0L,
            progressPercent = 0.0f,
            progressLocation = "0",
            currentChapterTitle = "Letter 1",
            readingStatus = "UNREAD",
            isFavorite = false,
            fileHash = "sample_hash_frank_3",
            totalPages = 140,
            fileSizeBytes = 280000
        )
        list.add(Pair(frankBook, frankParsed))

        // 4. The Art of Reading (PDF)
        val pdfTitle = "The Art of Reading"
        val pdfAuthor = "Mortimer J. Adler"
        val pdfFile = File(booksDir, "art_of_reading.pdf")

        val pageContents = listOf(
            Pair(
                "The Art of Reading",
                listOf(
                    "A Guide to Reading Comprehension and Critical Thought",
                    "Special Digital Edition for Readers",
                    "",
                    "Mortimer J. Adler & Charles Van Doren",
                    "",
                    "Published by Lumina Classics Library",
                    "",
                    "Swipe left or use the arrow buttons below to navigate between pages. Tap anywhere on the center to toggle reading controls, night mode, and font settings."
                )
            ),
            Pair(
                "Chapter 1: The Dimensions of Reading",
                listOf(
                    "Reading is a multi-dimensional activity. As you advance from the elementary level to inspectional, analytical, and syntopical reading, the demands upon your attention and intellect grow correspondingly.",
                    "",
                    "Active reading is fundamentally about asking questions while you read — questions which you yourself must try to answer in the course of reading.",
                    "",
                    "The first question to ask of any book is: What is the book about as a whole? You must try to discover the leading theme of the book, and how the author develops this theme in an orderly way by subdividing it into its essential subordinate topics."
                )
            ),
            Pair(
                "Chapter 2: The Rules of Inspectional Reading",
                listOf(
                    "Inspectional reading has two main phases: systematic skimming and superficial reading.",
                    "",
                    "1. Look at the title page and preface. Read each rapidly to identify the subject and scope.",
                    "2. Study the table of contents to obtain a general sense of the book's structure.",
                    "3. Check the index if the book has one — a quick glance reveals the range of topics covered.",
                    "4. Read the publisher's blurb and introductory summary.",
                    "",
                    "By completing these steps in fifteen minutes, you will discover whether the book warrants closer examination."
                )
            ),
            Pair(
                "Chapter 3: Analytical Reading",
                listOf(
                    "Analytical reading is thorough, complete, and the best reading you can do within a given amount of time.",
                    "",
                    "Rule 1: Classify the book according to kind and subject matter.",
                    "Rule 2: State what the whole book is about with the utmost brevity.",
                    "Rule 3: Enumerate its major parts in their order and relation.",
                    "Rule 4: Define the problem or problems the author is trying to solve.",
                    "",
                    "Only when you understand what the author is saying can you legitimately say: 'I agree', 'I disagree', or 'I suspend judgment'."
                )
            ),
            Pair(
                "Chapter 4: Syntopical Reading & Synthesis",
                listOf(
                    "Syntopical reading is reading many books on the same subject and comparing their conclusions.",
                    "",
                    "In syntopical reading, you and your issues are primary, not the authors of the individual books. You must construct the terms and propositions that enable an objective comparison of the distinct perspectives.",
                    "",
                    "Happy reading! Use Lumina Reader's swipe navigation, bookmarks, and night mode to enhance your daily reading practice."
                )
            )
        )

        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            try {
                val pdfDoc = android.graphics.pdf.PdfDocument()
                val pageWidth = 595
                val pageHeight = 842

                pageContents.forEachIndexed { idx, (header, paragraphs) ->
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, idx + 1).create()
                    val page = pdfDoc.startPage(pageInfo)
                    val canvas = page.canvas

                    // Canvas background
                    val bgPaint = Paint().apply { color = Color.parseColor("#FAF8F5") }
                    canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

                    // Header banner
                    val bannerPaint = Paint().apply { color = Color.parseColor("#1E293B") }
                    canvas.drawRect(0f, 0f, pageWidth.toFloat(), 60f, bannerPaint)

                    val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#F59E0B")
                        textSize = 18f
                        isFakeBoldText = true
                    }
                    canvas.drawText("LUMINA READER • DIGITAL EDITION", 40f, 38f, headerTextPaint)

                    // Title
                    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#0F172A")
                        textSize = 24f
                        isFakeBoldText = true
                    }
                    canvas.drawText(header, 40f, 120f, titlePaint)

                    // Divider line
                    val linePaint = Paint().apply {
                        color = Color.parseColor("#F59E0B")
                        strokeWidth = 3f
                    }
                    canvas.drawLine(40f, 135f, pageWidth - 40f, 135f, linePaint)

                    // Paragraphs
                    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#334155")
                        textSize = 16f
                    }
                    var curY = 175f
                    for (para in paragraphs) {
                        if (para.isEmpty()) {
                            curY += 16f
                            continue
                        }
                        val words = para.split(" ")
                        var line = ""
                        for (w in words) {
                            if (textPaint.measureText("$line $w".trim()) < (pageWidth - 80)) {
                                line = "$line $w".trim()
                            } else {
                                canvas.drawText(line, 40f, curY, textPaint)
                                curY += 24f
                                line = w
                            }
                        }
                        if (line.isNotEmpty()) {
                            canvas.drawText(line, 40f, curY, textPaint)
                            curY += 28f
                        }
                    }

                    // Footer page number
                    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#94A3B8")
                        textSize = 13f
                    }
                    canvas.drawText("Page ${idx + 1} of ${pageContents.size}", pageWidth / 2f - 40f, pageHeight - 35f, footerPaint)

                    pdfDoc.finishPage(page)
                }

                FileOutputStream(pdfFile).use { out ->
                    pdfDoc.writeTo(out)
                }
                pdfDoc.close()
            } catch (e: Exception) {
                // Handled
            }
        }

        val pdfCover = createSampleCover(pdfTitle, pdfAuthor, Color.parseColor("#1E3A8A"), Color.parseColor("#F59E0B"))
        val pdfCoverFile = File(coversDir, "cover_art_reading.png")
        FileOutputStream(pdfCoverFile).use { pdfCover.compress(Bitmap.CompressFormat.PNG, 95, it) }

        val basePdfParsed = PdfBookParser.parse(context, Uri.fromFile(pdfFile), pdfTitle)
        val sampleChapters = pageContents.mapIndexed { idx, (header, paragraphs) ->
            val cleanParas = paragraphs.filter { it.isNotBlank() }
            val fullText = "$header\n\n" + cleanParas.joinToString("\n\n")
            SpineChapter(
                id = "pdf_sample_$idx",
                title = header,
                plainText = fullText,
                formattedParagraphs = listOf(header) + cleanParas,
                wordCount = fullText.split("\\s+".toRegex()).count { it.isNotBlank() }
            )
        }
        val sampleToc = pageContents.mapIndexed { idx, (header, _) ->
            TocItem("pdf_sample_$idx", header, idx)
        }
        val pdfParsed = basePdfParsed.copy(chapters = sampleChapters, tableOfContents = sampleToc)

        val pdfBook = Book(
            id = 4,
            filePath = pdfFile.absolutePath,
            title = pdfTitle,
            author = pdfAuthor,
            format = "PDF",
            coverPath = pdfCoverFile.absolutePath,
            dateAdded = System.currentTimeMillis() - 86400000 * 2,
            lastOpened = System.currentTimeMillis() - 3600000,
            progressPercent = 20.0f,
            progressLocation = "0",
            currentChapterTitle = "Page 1",
            readingStatus = "READING",
            isFavorite = true,
            fileHash = "sample_hash_pdf_4",
            totalPages = 5,
            fileSizeBytes = pdfFile.length().coerceAtLeast(120000L)
        )
        list.add(Pair(pdfBook, pdfParsed))

        // Book 5: Real Internet PDF (TraceMonkey JIT - Mozilla Research)
        val internetPdfFile = File(booksDir, "tracemonkey.pdf")
        if (!internetPdfFile.exists() || internetPdfFile.length() == 0L) {
            try {
                context.assets.open("sample_books/tracemonkey.pdf").use { input ->
                    FileOutputStream(internetPdfFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                // Ignore if asset is not found
            }
        }

        if (internetPdfFile.exists() && internetPdfFile.length() > 0L) {
            val jitTitle = "TraceMonkey: JIT Compiler"
            val jitAuthor = "Mozilla Research"
            val jitCover = createSampleCover(jitTitle, jitAuthor, Color.parseColor("#0F172A"), Color.parseColor("#10B981"))
            val jitCoverFile = File(coversDir, "cover_tracemonkey.png")
            try {
                FileOutputStream(jitCoverFile).use { jitCover.compress(Bitmap.CompressFormat.PNG, 95, it) }
            } catch (e: Exception) {}

            val jitParsed = PdfBookParser.parse(context, Uri.fromFile(internetPdfFile), jitTitle)
            val jitBook = Book(
                id = 5,
                filePath = internetPdfFile.absolutePath,
                title = jitTitle,
                author = jitAuthor,
                format = "PDF",
                coverPath = jitCoverFile.absolutePath,
                dateAdded = System.currentTimeMillis() - 86400000,
                lastOpened = 0L,
                progressPercent = 0.0f,
                progressLocation = "0",
                currentChapterTitle = "Page 1",
                readingStatus = "UNREAD",
                isFavorite = false,
                fileHash = "sample_hash_pdf_tracemonkey_5",
                totalPages = jitParsed.totalPagesEstimate.coerceAtLeast(14),
                fileSizeBytes = internetPdfFile.length()
            )
            list.add(Pair(jitBook, jitParsed))
        }

        return list
    }
}
