package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AuctionDailyHotfixContractTest {
    @Test
    void auctionUsesThirtyMinuteListingsAndTwoHundredCoinBids() throws Exception {
        String auction = Files.readString(Path.of("src/main/java/hgloot/plugin/AuctionHouseService.java"));

        assertTrue(auction.contains("BID_STEP_COINS = 200"));
        assertTrue(auction.contains("AUCTION_DURATION_MS = 30L * 60L * 1000L"));
        assertTrue(auction.contains("Auction ends in 30m."));
        assertTrue(auction.contains("return AUCTION_DURATION_MS;"));
        assertTrue(auction.contains("Sound.ENTITY_EXPERIENCE_ORB_PICKUP"));
        assertTrue(auction.contains("meta.setLore(lightGrayLore"));
        assertTrue(auction.contains("ChatColor.stripColor"));
        assertFalse(auction.contains("Auction ends in 10m."));
        assertFalse(auction.contains("BID_STEP_SILVER"));
    }
}
