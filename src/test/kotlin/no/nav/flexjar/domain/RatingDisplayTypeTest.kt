package no.nav.flexjar.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class RatingVariantTest : FunSpec({

    context("RatingVariant.getScale") {
        test("EMOJI has fixed scale 5") {
            RatingVariant.getScale(RatingVariant.EMOJI) shouldBe 5
        }

        test("THUMBS has fixed scale 2") {
            RatingVariant.getScale(RatingVariant.THUMBS) shouldBe 2
        }

        test("STARS has fixed scale 5") {
            RatingVariant.getScale(RatingVariant.STARS) shouldBe 5
        }

        test("NPS has fixed scale 11 (0-10)") {
            RatingVariant.getScale(RatingVariant.NPS) shouldBe 11
        }
    }

    context("RatingVariant.fixedScales") {
        test("contains all variants with their fixed scales") {
            RatingVariant.fixedScales shouldBe mapOf(
                RatingVariant.EMOJI to 5,
                RatingVariant.THUMBS to 2,
                RatingVariant.STARS to 5,
                RatingVariant.NPS to 11
            )
        }
    }

    context("AnswerValue.Rating validation") {
        context("valid ratings") {
            test("emoji with rating 1-5") {
                val rating = AnswerValue.Rating(
                    rating = 3,
                    ratingVariant = RatingVariant.EMOJI
                )
                rating.rating shouldBe 3
            }

            test("thumbs with rating 1-2") {
                val rating1 = AnswerValue.Rating(
                    rating = 1,
                    ratingVariant = RatingVariant.THUMBS
                )
                rating1.rating shouldBe 1

                val rating2 = AnswerValue.Rating(
                    rating = 2,
                    ratingVariant = RatingVariant.THUMBS
                )
                rating2.rating shouldBe 2
            }

            test("stars with rating 1-5") {
                AnswerValue.Rating(rating = 1, ratingVariant = RatingVariant.STARS)
                AnswerValue.Rating(rating = 3, ratingVariant = RatingVariant.STARS)
                AnswerValue.Rating(rating = 5, ratingVariant = RatingVariant.STARS)
            }

            test("nps with rating 0-10") {
                val nps0 = AnswerValue.Rating(
                    rating = 0,
                    ratingVariant = RatingVariant.NPS
                )
                nps0.rating shouldBe 0

                val nps10 = AnswerValue.Rating(
                    rating = 10,
                    ratingVariant = RatingVariant.NPS
                )
                nps10.rating shouldBe 10
            }
        }

        context("out of bounds ratings") {
            test("emoji rating above 5 throws exception") {
                val exception = shouldThrow<IllegalArgumentException> {
                    AnswerValue.Rating(
                        rating = 6,
                        ratingVariant = RatingVariant.EMOJI
                    )
                }
                exception.message shouldContain "out of bounds"
            }

            test("emoji rating below 1 throws exception") {
                shouldThrow<IllegalArgumentException> {
                    AnswerValue.Rating(
                        rating = 0,
                        ratingVariant = RatingVariant.EMOJI
                    )
                }
            }

            test("thumbs rating above 2 throws exception") {
                shouldThrow<IllegalArgumentException> {
                    AnswerValue.Rating(
                        rating = 3,
                        ratingVariant = RatingVariant.THUMBS
                    )
                }
            }

            test("stars rating above 5 throws exception") {
                shouldThrow<IllegalArgumentException> {
                    AnswerValue.Rating(
                        rating = 6,
                        ratingVariant = RatingVariant.STARS
                    )
                }
            }

            test("nps rating above 10 throws exception") {
                shouldThrow<IllegalArgumentException> {
                    AnswerValue.Rating(
                        rating = 11,
                        ratingVariant = RatingVariant.NPS
                    )
                }
            }

            test("nps rating below 0 throws exception") {
                shouldThrow<IllegalArgumentException> {
                    AnswerValue.Rating(
                        rating = -1,
                        ratingVariant = RatingVariant.NPS
                    )
                }
            }
        }

        context("backward compatibility") {
            test("rating without variant is allowed (legacy)") {
                val legacy = AnswerValue.Rating(rating = 4)
                legacy.rating shouldBe 4
                legacy.ratingVariant shouldBe null
                legacy.ratingScale shouldBe null
            }

            test("rating with variant uses fixed scale automatically") {
                val rating = AnswerValue.Rating(
                    rating = 3,
                    ratingVariant = RatingVariant.EMOJI
                )
                rating.rating shouldBe 3
                // Scale is derived from variant, not stored separately
            }
        }
    }
})
