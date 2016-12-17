package mcmultipart.client;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import mcmultipart.MCMultiPart;
import mcmultipart.api.container.IPartInfo;
import mcmultipart.api.multipart.IMultipartTile;
import mcmultipart.api.slot.IPartSlot;
import mcmultipart.block.TileMultipartContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.SimpleBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;

public class TESRMultipartContainer extends TileEntitySpecialRenderer<TileMultipartContainer> {

    @Override
    public void renderTileEntityAt(TileMultipartContainer te, double x, double y, double z, float partialTicks, int destroyStage) {
        if (destroyStage >= 0) {
            RayTraceResult hit = Minecraft.getMinecraft().objectMouseOver;
            if (hit.getBlockPos().equals(te.getPos())) {
                IPartSlot slotHit = MCMultiPart.slotRegistry.getObjectById(hit.subHit);
                Optional<IPartInfo> infoOpt = te.get(slotHit);
                if (infoOpt.isPresent()) {
                    IPartInfo info = infoOpt.get();

                    if (info.getTile() != null && info.getTile().canRenderBreaking()) {
                        TileEntityRendererDispatcher.instance.renderTileEntityAt(info.getTile().getTileEntity(), x, y, z, partialTicks,
                                destroyStage);
                    } else {
                        if (MinecraftForgeClient.getRenderPass() == 1) {
                            IBlockState state = info.getPart().getActualState(info.getWorld(), info.getPos(), info);
                            IBakedModel model = Minecraft.getMinecraft().getBlockRendererDispatcher().getModelForState(state);
                            if (model != null) {
                                state = info.getPart().getExtendedState(info.getWorld(), info.getPos(), info, state);

                                TextureAtlasSprite breakingTexture = Minecraft.getMinecraft().getTextureMapBlocks()
                                        .getAtlasSprite("minecraft:blocks/destroy_stage_" + destroyStage);

                                startBreaking();
                                VertexBuffer buffer = Tessellator.getInstance().getBuffer();
                                buffer.begin(7, DefaultVertexFormats.BLOCK);
                                buffer.setTranslation(x - te.getPos().getX(), y - te.getPos().getY(), z - te.getPos().getZ());
                                buffer.noColor();

                                for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                                    if (info.getPart().canRenderInLayer(info.getWorld(), info.getPos(), info, state, layer)) {
                                        ForgeHooksClient.setRenderLayer(layer);
                                        Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelRenderer().renderModel(
                                                te.getWorld(),
                                                new SimpleBakedModel.Builder(state, model, breakingTexture, info.getPos()).makeBakedModel(),
                                                state, te.getPos(), buffer, true);
                                    }
                                }
                                ForgeHooksClient.setRenderLayer(BlockRenderLayer.SOLID);

                                buffer.setTranslation(0, 0, 0);
                                Tessellator.getInstance().draw();
                                finishBreaking();
                            }
                        }
                    }

                    return;
                }
            }
        }

        Set<TileEntity> fast = new HashSet<>(), slow = new HashSet<>();
        te.getParts().values().forEach(p -> {
            if (p.getTile() != null) {
                (p.getTile().hasFastRenderer() ? fast : slow).add(p.getTile().getTileEntity());
            }
        });

        if (!fast.isEmpty()) {
            Tessellator.getInstance().getBuffer().begin(7, DefaultVertexFormats.BLOCK);
            Tessellator.getInstance().getBuffer().setTranslation(0, 0, 0);

            VertexBuffer buffer = Tessellator.getInstance().getBuffer();
            fast.forEach(t -> TileEntityRendererDispatcher.instance.getSpecialRenderer(t).renderTileEntityFast(t, x, y, z, partialTicks,
                    destroyStage, buffer));

            Tessellator.getInstance().draw();
        }

        slow.forEach(t -> TileEntityRendererDispatcher.instance.renderTileEntityAt(t, x, y, z, partialTicks, destroyStage));
    }

    @Override
    public void renderTileEntityFast(TileMultipartContainer te, double x, double y, double z, float partialTicks, int destroyStage,
            VertexBuffer buffer) {
        te.getParts().values().forEach(p -> {
            IMultipartTile t = p.getTile();
            if (t != null && t.hasFastRenderer()) {
                TileEntityRendererDispatcher.instance.getSpecialRenderer(t.getTileEntity()).renderTileEntityFast(t.getTileEntity(), x, y, z,
                        partialTicks, destroyStage, buffer);
            }
        });
    }

    private static void startBreaking() {
        // For some reason I still don't understand, this works. Don't question it. Blame vanilla.
        GlStateManager.disableLighting();

        Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.5F);
        GlStateManager.doPolygonOffset(-3.0F, -3.0F);
        GlStateManager.enablePolygonOffset();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableAlpha();
        GlStateManager.pushMatrix();
    }

    private static void finishBreaking() {
        GlStateManager.disableAlpha();
        GlStateManager.doPolygonOffset(0.0F, 0.0F);
        GlStateManager.disablePolygonOffset();
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);
        GlStateManager.popMatrix();
        Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
    }

}
