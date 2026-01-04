package no.nav.flexjar.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class StatsServiceTest {
    
    private val service = StatsService()
    
    @Test
    fun `calculateAverageRating returns correct average`() {
        val byRating = mapOf("5" to 3, "4" to 5, "3" to 2)
        
        val average = service.calculateAverageRating(byRating)
        
        // (5*3 + 4*5 + 3*2) / 10 = (15 + 20 + 6) / 10 = 4.1
        assertEquals(4.1, average!!, 0.01)
    }
    
    @Test
    fun `calculateAverageRating returns null for empty map`() {
        val byRating = emptyMap<String, Int>()
        
        val average = service.calculateAverageRating(byRating)
        
        assertNull(average)
    }
    
    @Test
    fun `calculateAverageRating ignores non-numeric ratings`() {
        val byRating = mapOf("5" to 2, "good" to 5, "3" to 3)
        
        val average = service.calculateAverageRating(byRating)
        
        // Only count 5*2 and 3*3 = (10 + 9) / 5 = 3.8
        assertEquals(3.8, average!!, 0.01)
    }
    
    @Test
    fun `calculateDays returns correct number of days`() {
        val from = "2024-01-01"
        val to = "2024-01-15"
        
        val days = service.calculateDays(from, to)
        
        assertEquals(14, days)
    }
    
    @Test
    fun `calculateDays returns at least 1 for same day`() {
        val from = "2024-01-01"
        val to = "2024-01-01"
        
        val days = service.calculateDays(from, to)
        
        assertEquals(1, days)
    }
    
    @Test
    fun `calculateDays returns 30 for null values`() {
        val days = service.calculateDays(null, null)
        
        // Uses default of 30 days ago to now
        assertTrue(days >= 30)
    }
    
    @Test
    fun `calculateDays returns 30 for invalid dates`() {
        val from = "not-a-date"
        val to = "also-not-a-date"
        
        val days = service.calculateDays(from, to)
        
        assertEquals(30, days)
    }
}
