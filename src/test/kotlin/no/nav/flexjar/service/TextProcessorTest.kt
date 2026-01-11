package no.nav.flexjar.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class TextProcessorTest : FunSpec({

    context("extractWords") {
        test("filters out stop words") {
            val text = "Jeg har en søknad og det er bra"
            val words = TextProcessor.extractWords(text)
            
            words shouldNotContain "jeg"
            words shouldNotContain "har"
            words shouldNotContain "en"
            words shouldNotContain "og"
            words shouldContain "søknad"
        }

        test("removes special characters") {
            val text = "Hallo! Dette er en test-melding."
            val words = TextProcessor.extractWords(text)
            
            words shouldContain "hallo"
            words shouldContain "test"
            words shouldContain "melding"
        }
    }

    context("stemNorwegian") {
        test("stems definite plural") {
            TextProcessor.stemNorwegian("søknadene") shouldBe "søknad"
        }

        test("stems definite singular") {
            TextProcessor.stemNorwegian("søknaden") shouldBe "søknad"
            TextProcessor.stemNorwegian("huset") shouldBe "hus"
        }

        test("stems indefinite plural") {
            TextProcessor.stemNorwegian("bilen") shouldBe "bil"
            TextProcessor.stemNorwegian("biler") shouldBe "bil"
        }

        test("does not stem short words") {
            TextProcessor.stemNorwegian("et") shouldBe "et"
            TextProcessor.stemNorwegian("en") shouldBe "en"
        }
    }
})
