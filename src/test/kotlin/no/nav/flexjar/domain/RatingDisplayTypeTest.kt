package no.nav.flexjar.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class RatingDisplayTypeTest : FunSpec({

    context("RatingDisplayType.isValidCombination") {
        test("EMOJI only supports scale 5") {
            RatingDisplayType.isValidCombination(RatingDisplayType.EMOJI, 5) shouldBe true
            RatingDisplayType.isValidCombination(RatingDisplayType.EMOJI, 3) shouldBe false
            RatingDisplayType.isValidCombination(RatingDisplayType.EMOJI, 10) shouldBe false
        }

        test("THUMBS only supports scale 2") {
            RatingDisplayType.isValidCombination(RatingDisplayType.THUMBS, 2) shouldBe true
            RatingDisplayType.isValidCombination(RatingDisplayType.THUMBS, 5) shouldBe false
        }

        test("STAR supports scales 3, 5, 7, and 10") {
            RatingDisplayType.isValidCombination(RatingDisplayType.STAR, 3) shouldBe true
            RatingDisplayType.isValidCombination(RatingDisplayType.STAR, 5) shouldBe true
            RatingDisplayType.isValidCombination(RatingDisplayType.STAR, 7) shouldBe true
            RatingDisplayType.isValidCombination(RatingDisplayType.STAR, 10) shouldBe true
            RatingDisplayType.isValidCombination(RatingDisplayType.STAR, 4) shouldBe false
            RatingDisplayType.isValidCombination(RatingDisplayType.STAR, 11) shouldBe false
        }

        test("NPS only supports scale 11 (0-10)") {
            RatingDisplayType.isValidCombination(RatingDisplayType.NPS, 11) shouldBe true
            RatingDisplayType.isValidCombination(RatingDisplayType.NPS, 10) shouldBe false
        }
    }

    context("AnswerValue.Rating validation") {
        context("valid combinations") {
            test("emoji with scale 5 and rating 1-5") {
                val rating = AnswerValue.Rating(
                    rating = 3,
                    ratingDisplayType = RatingDisplayType.EMOJI,
                    ratingScale = 5
                )
                rating.rating shouldBe 3
            }

            test("thumbs with scale 2 and rating 1-2") {
                val rating1 = AnswerValue.Rating(
                    rating = 1,
                    ratingDisplayType = RatingDisplayType.THUMBS,
                    ratingScale = 2
                )
                rating1.rating shouldBe 1

                val rating2 = AnswerValue.Rating(
                    rating = 2,
                    ratingDisplayType = RatingDisplayType.THUMBS,
                    ratingScale = 2
                )
                rating2.rating shouldBe 2
            }

            test("star with various scales") {
                AnswerValue.Rating(rating = 3, ratingDisplayType = RatingDisplayType.STAR, ratingScale = 3)
                AnswerValue.Rating(rating = 5, ratingDisplayType = RatingDisplayType.STAR, ratingScale = 5)
                AnswerValue.Rating(rating = 7, ratingDisplayType = RatingDisplayType.STAR, ratingScale = 7)
                AnswerValue.Rating(rating = 10, ratingDisplayType = RatingDisplayType.STAR, ratingScale = 10)
            }

            test("nps with scale 11 and rating 0-10") {
                val nps0 = AnswerValue.Rating(
                    rating = 0,
                    ratingDisplayType = RatingDisplayType.NPS,
                    ratingScale = 11
                )
                nps0.rating shouldBe 0

                val nps10 = AnswerValue.Rating(
                    rating = 10,
                    ratingDisplayType = RatingDisplayType.NPS,
                    ratingScale = 11
                )
                nps10.rating shouldBe 10
            }
        }

        context("invalid scale combinations") {
            test("emoji with wrong scale throws exception") {
                val exception = shouldThrow<IllegalArgumentException> {
                    AnswerValue.Rating(
                        rating = 3,
                        ratingDisplayType = RatingDisplayType.EMOJI,
                        ratingScale = 10
                    )
                }
                exception.message shouldContain "Invalid combination"
                exception.message shouldContain "EMOJI"
                exception.message shouldContain "10"
            }

            test("thumbs with wrong scale throws exception") {
                shouldThrow<IllegalArgumentException> {
                    AnswerValue.Rating(
                        rating = 1,
                        ratingDisplayType = RatingDisplayType.THUMBS,
                        ratingScale = 5
                    )
                }
            }

            test("nps with wrong scale throws exception") {
                shouldThrow<IllegalArgumentException> {
                    AnswerValue.Rating(
                        rating = 5,
                        ratingDisplayType = RatingDisplayType.NPS,
                        ratingScale = 10
                    )
                }
            }
        }

        context("out of bounds ratings") {
            test("emoji rating above 5 throws exception") {
                val exception = shouldThrow<IllegalArgumentException> {
                    AnswerValue.Rating(
                        rating = 6,
                        ratingDisplayType = RatingDisplayType.EMOJI,
                        ratingScale = 5
                    )
                }
                exception.message shouldContain "out of bounds"
            }

            test("emoji rating below 1 throws exception") {
                shouldThrow<IllegalArgumentException> {
                    AnswerValue.Rating(
                        rating = 0,
                        ratingDisplayType = RatingDisplayType.EMOJI,
                        ratingScale = 5
                    )
                }
            }

            test("thumbs rating above 2 throws exception") {
                shouldThrow<IllegalArgumentException> {
                    AnswerValue.Rating(
                        rating = 3,
                        ratingDisplayType = RatingDisplayType.THUMBS,
                        ratingScale = 2
                    )
                }
            }

            test("nps rating above 10 throws exception") {
                shouldThrow<IllegalArgumentException> {
                    AnswerValue.Rating(
                        rating = 11,
                        ratingDisplayType = RatingDisplayType.NPS,
                        ratingScale = 11
                    )
                }
            }

            test("nps rating below 0 throws exception") {
                shouldThrow<IllegalArgumentException> {
                    AnswerValue.Rating(
                        rating = -1,
                        ratingDisplayType = RatingDisplayType.NPS,
                        ratingScale = 11
                    )
                }
            }
        }

        context("backward compatibility") {
            test("rating without displayType or scale is allowed (legacy)") {
                val legacy = AnswerValue.Rating(rating = 4)
                legacy.rating shouldBe 4
                legacy.ratingDisplayType shouldBe null
                legacy.ratingScale shouldBe null
            }

            test("rating with only displayType is allowed") {
                val partial = AnswerValue.Rating(
                    rating = 3,
                    ratingDisplayType = RatingDisplayType.EMOJI
                )
                partial.rating shouldBe 3
                partial.ratingScale shouldBe null
            }

            test("rating with only scale is allowed") {
                val partial = AnswerValue.Rating(
                    rating = 3,
                    ratingScale = 5
                )
                partial.rating shouldBe 3
                partial.ratingDisplayType shouldBe null
            }
        }
    }
})
