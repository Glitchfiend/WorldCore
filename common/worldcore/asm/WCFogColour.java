package worldcore.asm;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.ForgeDummyContainer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import worldcore.interfaces.IWCFog;

public class WCFogColour implements IClassTransformer
{
    @Override
    public byte[] transform(String name, String newname, byte[] bytes)
    {
        if (name.equals("net.minecraft.client.renderer.EntityRenderer")) 
        {
            return patchEntityRenderer(newname, bytes, false);
        }
        
        if (name.equals("bfe")) 
        {
            return patchEntityRenderer(newname, bytes, true);
        }
        
        return bytes;
    }

    public static byte[] patchEntityRenderer(String name, byte[] bytes, boolean obfuscated)
    {
        ClassReader classReader = new ClassReader(bytes);
        ClassNode classNode = new ClassNode();

        classReader.accept(classNode, 0);

        boolean found = false;

        for (MethodNode methodNode : classNode.methods)
        {
            ListIterator iterator;

            if ((methodNode.name.equals("updateFogColor")) || (FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(classNode.name, methodNode.name, methodNode.desc).equals("func_78466_h")))
            {
                for (iterator = methodNode.instructions.iterator(); iterator.hasNext();) 
                {
                    AbstractInsnNode insnNode = (AbstractInsnNode)iterator.next();

                    if ((insnNode instanceof MethodInsnNode)) 
                    {
                        MethodInsnNode node = (MethodInsnNode)insnNode;

                        if ((node.name.equals("getFogColor")) || (FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(node.name, node.name, node.desc).equals("func_72948_g")))
                        {
                            InsnList toInject = new InsnList();

                            toInject.add(new VarInsnNode(ALOAD, 3));
                            toInject.add(new VarInsnNode(ALOAD, 2));
                            toInject.add(new VarInsnNode(FLOAD, 1));
                            if (obfuscated)
                                toInject.add(new MethodInsnNode(INVOKESTATIC, "worldcore/asm/WCFogColour", "getFogVec", "(Lnn;Labw;F)Latc;"));
                            else
                                toInject.add(new MethodInsnNode(INVOKESTATIC, "worldcore/asm/WCFogColour", "getFogVec", "(Lnet/minecraft/entity/Entity;Lnet/minecraft/world/World;F)Lnet/minecraft/util/Vec3;"));
                            toInject.add(new VarInsnNode(ASTORE, 9));

                            methodNode.instructions.insert(node.getNext(), toInject);

                            List<AbstractInsnNode> remNodes = new ArrayList();

                            AbstractInsnNode currentNode = node.getPrevious().getPrevious();

                            for (int i = -2; i <= 1; i++)
                            {
                                remNodes.add(currentNode);
                                currentNode = currentNode.getNext();
                            }

                            for (AbstractInsnNode remNode : remNodes)
                            {
                                methodNode.instructions.remove(remNode);
                            }

                            found = true;
                            break;
                        }
                    }
                }

                if (found)
                    break;
            }
        }

        if (!found) throw new RuntimeException("setupFog transformer failed"); 

        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);

        return classWriter.toByteArray();
    }

    public static int getFogBlendColour(World world, float partialRenderTick, int playerX, int playerZ)
    {
        int distance = Minecraft.getMinecraft().gameSettings.fancyGraphics ? ForgeDummyContainer.blendRanges[Minecraft.getMinecraft().gameSettings.renderDistance] : 0;
        
        int r = 0;
        int g = 0;
        int b = 0;
        
        float celestialAngle = world.getCelestialAngle(partialRenderTick);
        
        int wr = (int)(world.provider.getFogColor(celestialAngle, partialRenderTick).xCoord * 255);
        int wg = (int)(world.provider.getFogColor(celestialAngle, partialRenderTick).yCoord * 255);
        int wb = (int)(world.provider.getFogColor(celestialAngle, partialRenderTick).zCoord * 255);
        
        int defaultcolour = (wr << 16) + (wg << 8) + (wb);
        int divider = 0;
        
        for (int x = -distance; x <= distance; ++x)
        {
            for (int z = -distance; z <= distance; ++z)
            {
                BiomeGenBase biome = world.getBiomeGenForCoords(playerX + x, playerZ + z);
                int colour = 0;
                
                if (biome instanceof IWCFog)
                {
                    colour = ((IWCFog)biome).getFogColour();
                }
                else
                {
                    colour = defaultcolour;
                }
                    
                r += (colour & 0xFF0000) >> 16;
                g += (colour & 0x00FF00) >> 8;
                b += colour & 0x0000FF;
                divider++;
            }
        }
        
        float celestialAngleMultiplier = MathHelper.cos(celestialAngle * (float)Math.PI * 2.0F) * 2.0F + 0.5F;

        if (celestialAngleMultiplier < 0.0F)
        {
            celestialAngleMultiplier = 0.0F;
        }

        if (celestialAngleMultiplier > 1.0F)
        {
            celestialAngleMultiplier = 1.0F;
        }
        
        r *= celestialAngleMultiplier;
        g *= celestialAngleMultiplier;
        b *= celestialAngleMultiplier;

        int multiplier = (r / divider & 255) << 16 | (g / divider & 255) << 8 | b / divider & 255;

        return multiplier;
    }
    
    public static Vec3 getFogVec(Entity entity, World world, float partialRenderTick)
    {
        int x = MathHelper.floor_double(entity.posX);
        int z = MathHelper.floor_double(entity.posZ);
        
        int multiplier = getFogBlendColour(world, partialRenderTick, x, z);
        
        float r = (float)(multiplier >> 16 & 255) / 255.0F;
        float g = (float)(multiplier >> 8 & 255) / 255.0F;
        float b = (float)(multiplier & 255) / 255.0F;
        
        return world.getWorldVec3Pool().getVecFromPool(r, g, b);
    }
}
