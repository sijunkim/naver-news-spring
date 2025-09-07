package com.news.naver.controller

import com.news.naver.service.ManualService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ManualController(
    private val manualService: ManualService
) {

    @PostMapping("/manual/news/dev")
    suspend fun triggerDevNews(): ResponseEntity<String> {
        manualService.runDevNewsPoll()
        return ResponseEntity.ok("Dev news poll triggered.")
    }

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

    @DeleteMapping("/manual/reset-all-data")
    suspend fun deleteRuntimeData(): ResponseEntity<String> {
        manualService.resetAllData()
        return ResponseEntity.ok("Runtime data reset.")
    }

    @GetMapping("/manual/domain-check")
    suspend fun checkDomains(
        @RequestParam("domain") domain: String
    ): ResponseEntity<String> {
        val title = manualService.getDomain(domain)
        return ResponseEntity.ok(title)
    }
}