package com.example.domain.model

/**
 * Represents a whitelisted domain for SNI (Server Name Indication) spoofing / domain fronting.
 * Used in restricted networks where DPI only permits traffic to specific white-listed domestic services.
 */
data class SniDomain(
    val id: String,
    val domain: String,
    val serviceName: String,
    val category: String,
    val country: String = "RU"
)

object SniCatalog {
    val russianWhitelistDomains: List<SniDomain> = listOf(
        SniDomain("vk", "vk.com", "VKontakte Social / CDN", "Social Network"),
        SniDomain("vk_api", "api.vk.com", "VKontakte API", "Social Network"),
        SniDomain("vk_user", "userapi.com", "VK User Content CDN", "CDN"),
        SniDomain("yandex", "yandex.ru", "Yandex Portal & Search", "Portal"),
        SniDomain("ya_disk", "disk.yandex.ru", "Yandex Disk Cloud", "Cloud Storage"),
        SniDomain("dzen", "dzen.ru", "Dzen Media & News", "Media"),
        SniDomain("gosuslugi", "gosuslugi.ru", "Gosuslugi State Services", "Government"),
        SniDomain("sber", "sberbank.ru", "SberBank Online", "Banking"),
        SniDomain("sber_api", "api.sberbank.ru", "SberBank Gateway", "Banking"),
        SniDomain("tbank", "tbank.ru", "T-Bank (Tinkoff)", "Banking"),
        SniDomain("tinkoff", "tinkoff.ru", "Tinkoff Portal", "Banking"),
        SniDomain("ozon", "ozon.ru", "Ozon Marketplace", "E-Commerce"),
        SniDomain("wildberries", "wildberries.ru", "Wildberries Marketplace", "E-Commerce"),
        SniDomain("wb_cdn", "wb.ru", "Wildberries CDN", "E-Commerce"),
        SniDomain("mailru", "mail.ru", "Mail.ru Webmail & Cloud", "Portal"),
        SniDomain("rutube", "rutube.ru", "Rutube Video Platform", "Video"),
        SniDomain("mosru", "mos.ru", "Moscow City Portal", "Government"),
        SniDomain("avito", "avito.ru", "Avito Classifieds", "Marketplace"),
        SniDomain("rbc", "rbc.ru", "RBC Information Agency", "News"),
        SniDomain("kinopoisk", "kinopoisk.ru", "Kinopoisk Streaming", "Entertainment"),
        SniDomain("lenta", "lenta.ru", "Lenta.ru News", "News"),
        SniDomain("ria", "ria.ru", "RIA Novosti", "News"),
        SniDomain("habr", "habr.com", "Habr IT Community", "Tech"),
        SniDomain("megafon", "megafon.ru", "MegaFon Telecom", "Telecom"),
        SniDomain("mts", "mts.ru", "MTS Telecom", "Telecom"),
        SniDomain("beeline", "beeline.ru", "Beeline Telecom", "Telecom"),
        SniDomain("tele2", "tele2.ru", "T2 (Tele2) Telecom", "Telecom"),
        SniDomain("rostelecom", "rt.ru", "Rostelecom Backbone", "Telecom"),
        SniDomain("kaspersky", "kaspersky.ru", "Kaspersky Security", "Security"),
        SniDomain("headhunter", "hh.ru", "HeadHunter Jobs", "Job Search"),
        SniDomain("one_tv", "1tv.ru", "Channel One Russia", "TV Broadcasting")
    )

    fun getRandomRussianSni(): String {
        return russianWhitelistDomains.random().domain
    }
}
