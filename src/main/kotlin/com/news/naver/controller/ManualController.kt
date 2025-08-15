package com.news.naver.controller

import com.news.naver.service.ManualService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ManualController(
    private val manualService: ManualService
) {

    @PostMapping("/manual/news/breaking")
    suspend fun triggerBreakingNews(): ResponseEntity<String> {
        manualService.runBreakingNewsPoll()
        return ResponseEntity.ok("Breaking news poll triggered.")
    }

    @PostMapping("/manual/news/exclusive")
    suspend fun triggerExclusiveNews(): ResponseEntity<String> {
        manualService.runExclusiveNewsPoll()
        return ResponseEntity.ok("Exclusive news poll triggered.")
    }

    @DeleteMapping("/manual/keywords/spam")
    suspend fun deleteSpamKeywords(): ResponseEntity<String> {
        val count = manualService.resetSpamKeywords()
        return ResponseEntity.ok("Deleted $count spam keyword entries.")
    }

    @DeleteMapping("/manual/polls/timestamp")
    suspend fun deletePollTimestamps(): ResponseEntity<String> {
        val count = manualService.deletePollTimestamps()
        return ResponseEntity.ok("Deleted $count poll timestamp entries.")
    }

    @DeleteMapping("/manual/runtime-data")
    suspend fun deleteRuntimeData(): ResponseEntity<String> {
        manualService.resetRuntimeData()
        return ResponseEntity.ok("Runtime data reset.")
    }
}