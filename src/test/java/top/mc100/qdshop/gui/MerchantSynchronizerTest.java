package top.mc100.qdshop.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MerchantSynchronizerTest {
    public interface PlayerHandle { NativePlayer getHandle(); }
    public interface MerchantHandle { NativeMerchant getMerchant(); }
    public static class Offers {}
    public static class Menu { public int containerId = 5; }
    public static class NativeMerchant {
        final Offers offers = new Offers();
        public Offers getOffers() { return offers; }
    }
    public static class NativePlayer {
        public Menu containerMenu = new Menu();
        int sentId; Offers sentOffers;
        public void sendMerchantOffers(int id, Offers offers, int level, int xp, boolean progress, boolean restock) {
            sentId = id; sentOffers = offers;
        }
    }

    @Test void syncResendsNativeOffersInSameWindowWithoutRemovingPayments() throws Exception {
        Player player = mock(Player.class, withSettings().extraInterfaces(PlayerHandle.class));
        Merchant merchant = mock(Merchant.class, withSettings().extraInterfaces(MerchantHandle.class));
        NativePlayer handle = new NativePlayer(); NativeMerchant nativeMerchant = new NativeMerchant();
        when(((PlayerHandle) player).getHandle()).thenReturn(handle);
        when(((MerchantHandle) merchant).getMerchant()).thenReturn(nativeMerchant);
        MerchantInventory inventory = mock(MerchantInventory.class);
        InventoryView view = mock(InventoryView.class);
        when(player.getOpenInventory()).thenReturn(view); when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getMerchant()).thenReturn(merchant);
        ItemStack input = mock(ItemStack.class); when(inventory.getItem(0)).thenReturn(input);
        MerchantSynchronizer.sync(player, merchant);
        assertEquals(5, handle.sentId); assertSame(nativeMerchant.offers, handle.sentOffers);
        verify(inventory).setItem(0, input);
        verify(inventory, never()).setItem(eq(1), any());
        verify(player).updateInventory(); verify(player, never()).closeInventory();
    }
}
