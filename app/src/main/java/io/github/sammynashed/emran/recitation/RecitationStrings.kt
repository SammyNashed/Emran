package io.github.sammynashed.emran.recitation

/**
 * Recitation Check copy, in both of Emran's languages.
 *
 * The English wording is fixed by product decision; the Arabic is a faithful
 * rendering of the same meaning rather than a literal word-for-word mapping,
 * which would read poorly. Kept in one place so the two can be compared.
 */
data class RecitationStrings(
    val title: String,
    val privateTitle: String,
    val privateBody: String,
    val modelTitle: String,
    val modelBody: String,
    val addModel: String,
    val checkingFile: String,
    val importNote: String,
    val whyTitle: String,
    val why1: String,
    val why2: String,
    val why3: String,
    val ready: String,
    val installedBody: String,
    val removeNote: String,
    val prompt: String,
    val start: String,
    val stop: String,
    val preparing: String,
    val listening: String,
    val removeModel: String,
    val allMatched: String,
    val missed: String,
    val repeated: String,
    val cutShort: String,
    val unclear: String,
    val unclearNote: String,
    val cannotPrepare: String,
    val ayah: String,
    val previous: String,
    val next: String,
    val close: String,
    val micNeeded: String,
    val downloadModel: String,
    val importInstead: String,
    val confirmTitle: String,
    val confirmBody: String,
    val confirmYes: String,
    val confirmNo: String,
    val downloading: String,
    val connecting: String,
    val connectingSlow: String,
    val paused: String,
    val pause: String,
    val resume: String,
    val cancelDownload: String,
    val downloadCancelled: String,
    val verifying: String,
    val downloadUnreachable: String,
    val downloadInterrupted: String,
    val downloadServerError: String,
    val retry: String,
    val verifyFailed: String,
) {
    companion object {
        fun forLanguage(lang: String): RecitationStrings =
            if (lang == "ar") ARABIC else ENGLISH

        val ENGLISH = RecitationStrings(
            title = "Recitation Check",
            privateTitle = "Private by design",
            privateBody = "Your recitation is processed entirely on this device. " +
                "Nothing is uploaded or sent to a server.",
            modelTitle = "Optional recitation model",
            modelBody = "This feature needs one extra file. Emran will never fetch it " +
                "on its own - you decide, and it happens once.",
            addModel = "Add model",
            checkingFile = "Checking file…",
            importNote = "Prefer to stay fully offline? Import the file yourself " +
                "instead. Either way, you can remove it at any time.",
            whyTitle = "Why isn't it included?",
            why1 = "Emran is offline-first. Prayer times, the Quran, Adhkar and " +
                "everything personal are calculated and stored on your device, and " +
                "none of it is ever sent anywhere. That does not change.",
            why2 = "Recitation Check needs a ~31 MB model file, and not everyone will " +
                "use it. Bundling it would make every person carry it, so instead it " +
                "is downloaded only if you ask for it - a single file, one time. If " +
                "you never turn this on, Emran never goes online at all.",
            why3 = "That download is the only thing in Emran that uses the internet, " +
                "and it is optional. Your recitation is never uploaded - it is " +
                "processed on this device, always. You can also import the file " +
                "yourself and keep Emran completely offline.",
            ready = "Recitation model installed",
            installedBody = "Hifz mode can use it. Open a surah, choose Hifz, and the words appear as you recite them.",
            removeNote = "Removing it frees the space. You can download it again at any time, or import the file yourself.",
            prompt = "Recite this ayah aloud and Emran will show you, word by word, " +
                "what it heard.",
            start = "Start reciting",
            stop = "Stop",
            preparing = "Preparing…",
            listening = "Listening — these may still change",
            removeModel = "Remove model and free space",
            allMatched = "All words matched.",
            missed = "missed",
            repeated = "repeated",
            cutShort = "cut short",
            unclear = "unclear",
            unclearNote = "\"Unclear\" means Emran could not tell — not that you were wrong.",
            cannotPrepare = "This ayah could not be prepared for recitation checking.",
            ayah = "Ayah",
            previous = "Previous ayah",
            next = "Next ayah",
            close = "Close",
            micNeeded = "Microphone access is needed to check your recitation.",
            downloadModel = "Download model",
            importInstead = "Import from file instead",
            confirmTitle = "Download recitation model?",
            confirmBody = "Emran will download one 31 MB file, this once. It is the " +
                "only time Emran uses the internet, and it stops as soon as the file " +
                "arrives. Your recitation is never uploaded.",
            confirmYes = "Download",
            confirmNo = "Cancel",
            downloading = "Downloading…",
            connecting = "Connecting…",
            connectingSlow = "Taking longer than usual. Your connection may be slow, or GitHub may be unreachable from your network without a VPN.",
            paused = "Paused",
            pause = "Pause download",
            resume = "Resume download",
            cancelDownload = "Cancel download",
            downloadCancelled = "Download cancelled. Nothing was kept.",
            verifying = "Verifying…",
            downloadUnreachable = "Could not reach the download. Check your internet " +
                "connection and try again. In some regions GitHub needs a VPN to " +
                "be reachable - or import the file yourself instead.",
            downloadInterrupted = "The connection dropped partway through. Try again, " +
                "or import the file yourself instead.",
            downloadServerError = "The model is not available at the moment. You can " +
                "import the file yourself instead.",
            retry = "Try again",
            verifyFailed = "The downloaded file did not match the expected model and " +
                "was discarded.",
        )

        val ARABIC = RecitationStrings(
            title = "تدقيق التلاوة",
            privateTitle = "خصوصية بالتصميم",
            privateBody = "تتم معالجة تلاوتك بالكامل على هذا الجهاز. " +
                "لا يتم رفع أي شيء أو إرساله إلى أي خادم.",
            modelTitle = "نموذج التلاوة الاختياري",
            modelBody = "تحتاج هذه الميزة إلى ملف إضافي واحد. لن ينزّله عمران من تلقاء " +
                "نفسه أبدًا — القرار قرارك، ويحدث مرة واحدة فقط.",
            addModel = "إضافة النموذج",
            checkingFile = "جارٍ التحقق من الملف…",
            importNote = "تفضّل البقاء دون اتصال تمامًا؟ استورد الملف بنفسك بدلًا من " +
                "ذلك. وفي الحالتين يمكنك إزالته في أي وقت.",
            whyTitle = "لماذا ليس مضمّنًا؟",
            why1 = "عمران يعمل دون اتصال أولًا. مواقيت الصلاة والقرآن والأذكار وكل " +
                "بياناتك الشخصية تُحسب وتُحفظ على جهازك، ولا يُرسل أي منها إلى أي " +
                "مكان. وهذا لا يتغير.",
            why2 = "يحتاج تدقيق التلاوة إلى ملف نموذج بحجم ٣١ ميجابايت تقريبًا، ولن " +
                "يستخدمه الجميع. تضمينه في التطبيق يعني أن يحمله كل شخص، لذلك يُنزَّل " +
                "فقط إن طلبته أنت — ملف واحد، مرة واحدة. وإن لم تُفعّل هذه الميزة، " +
                "فلن يتصل عمران بالإنترنت إطلاقًا.",
            why3 = "هذا التنزيل هو الشيء الوحيد في عمران الذي يستخدم الإنترنت، وهو " +
                "اختياري. لا تُرفع تلاوتك أبدًا — تتم معالجتها على هذا الجهاز دائمًا. " +
                "ويمكنك أيضًا استيراد الملف بنفسك لتُبقي عمران بلا اتصال تمامًا.",
            ready = "نموذج التلاوة مثبَّت",
            installedBody = "يمكن لوضع الحفظ استخدامه. افتح سورة، واختر الحفظ، وستظهر الكلمات كلما تلوتها.",
            removeNote = "إزالته تحرّر المساحة. يمكنك تنزيله مرة أخرى في أي وقت، أو استيراد الملف بنفسك.",
            prompt = "اتلُ هذه الآية بصوتٍ مسموع وسيعرض لك عمران، كلمةً كلمة، ما سمعه.",
            start = "ابدأ التلاوة",
            stop = "إيقاف",
            preparing = "جارٍ التحضير…",
            listening = "يستمع — قد تتغير هذه النتائج",
            removeModel = "إزالة النموذج وتحرير المساحة",
            allMatched = "طابقت جميع الكلمات.",
            missed = "لم تُتلَ",
            repeated = "مكررة",
            cutShort = "غير مكتملة",
            unclear = "غير واضحة",
            unclearNote = "«غير واضحة» تعني أن عمران لم يستطع التمييز — لا أنك أخطأت.",
            cannotPrepare = "تعذّر تحضير هذه الآية لتدقيق التلاوة.",
            ayah = "آية",
            previous = "الآية السابقة",
            next = "الآية التالية",
            close = "إغلاق",
            micNeeded = "يلزم الإذن بالوصول إلى الميكروفون لتدقيق التلاوة.",
            downloadModel = "تنزيل النموذج",
            importInstead = "استيراد من ملف بدلًا من ذلك",
            confirmTitle = "تنزيل نموذج التلاوة؟",
            confirmBody = "سينزّل عمران ملفًا واحدًا بحجم ٣١ ميجابايت، هذه المرة فقط. " +
                "هي المرة الوحيدة التي يستخدم فيها عمران الإنترنت، ويتوقف فور وصول " +
                "الملف. ولا تُرفع تلاوتك أبدًا.",
            confirmYes = "تنزيل",
            confirmNo = "إلغاء",
            downloading = "جارٍ التنزيل…",
            connecting = "جارٍ الاتصال…",
            connectingSlow = "يستغرق الاتصال وقتًا أطول من المعتاد. قد يكون اتصالك بطيئًا، أو قد يتعذّر الوصول إلى GitHub من شبكتك دون VPN.",
            paused = "موقوف مؤقتًا",
            pause = "إيقاف التنزيل مؤقتًا",
            resume = "متابعة التنزيل",
            cancelDownload = "إلغاء التنزيل",
            downloadCancelled = "تم إلغاء التنزيل. لم يُحفظ أي شيء.",
            verifying = "جارٍ التحقق…",
            downloadUnreachable = "تعذّر الوصول إلى التنزيل. تحقّق من اتصالك بالإنترنت " +
                "وحاول مرة أخرى. في بعض المناطق يحتاج GitHub إلى VPN ليكون متاحًا — " +
                "أو استورد الملف بنفسك بدلًا من ذلك.",
            downloadInterrupted = "انقطع الاتصال في منتصف التنزيل. حاول مرة أخرى، أو " +
                "استورد الملف بنفسك بدلًا من ذلك.",
            downloadServerError = "النموذج غير متاح حاليًا. يمكنك استيراد الملف بنفسك " +
                "بدلًا من ذلك.",
            retry = "إعادة المحاولة",
            verifyFailed = "الملف الذي تم تنزيله لا يطابق النموذج المتوقع وتم حذفه.",
        )
    }
}
