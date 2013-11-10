package worldcore.asm;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import java.util.Iterator;
import java.util.ListIterator;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.ForgeDummyContainer;

import org.lwjgl.opengl.GL11;
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

public class WCFogDistance implements IClassTransformer
{
    private static int fogX, fogZ;

    private static boolean fogInit;
    private static float storedFinalFogCloseness;
    
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
            
            if ((methodNode.name.equals("setupFog")) || (methodNode.name.equals("a")))
            {
                for (iterator = methodNode.instructions.iterator(); iterator.hasNext();) 
                {
                    AbstractInsnNode insnNode = (AbstractInsnNode)iterator.next();

                    if ((insnNode instanceof FieldInsnNode)) 
                    {
                        FieldInsnNode node = (FieldInsnNode)insnNode;

                        if ((node.name.equals("mc")) || (node.name.equals("q")))
                        {
                            if (node.getNext() instanceof FieldInsnNode)
                            {
                                FieldInsnNode worldNode = (FieldInsnNode)node.getNext();

                                if ((worldNode.name.equals("theWorld")) || (worldNode.name.equals("f")))
                                {
                                    if (worldNode.getNext() instanceof FieldInsnNode)
                                    {
                                        FieldInsnNode providerNode = (FieldInsnNode)worldNode.getNext();

                                        if ((providerNode.name.equals("provider")) || (providerNode.name.equals("t")))
                                        {
                                            if (providerNode.getNext().getOpcode() == ALOAD)
                                            {
                                                InsnList toInject = new InsnList();
                                                
                                                toInject.add(new VarInsnNode(ALOAD, 3));
                                                toInject.add(new VarInsnNode(ILOAD, 1));
                                                toInject.add(new VarInsnNode(FLOAD, 6));
                                                if (obfuscated)
                                                    toInject.add(new MethodInsnNode(INVOKESTATIC, "worldcore/asm/WCFogDistance", "setBiomeFogDistance", "(Lnn;IF)V"));
                                                else
                                                    toInject.add(new MethodInsnNode(INVOKESTATIC, "worldcore/asm/WCFogDistance", "setBiomeFogDistance", "(Lnet/minecraft/entity/Entity;IF)V"));
                                                
                                                methodNode.instructions.insertBefore(node.getPrevious(), toInject);
                                                
                                                found = true;
                                                break;
                                            }                                         
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (found)
                break;
        }
        
        if (!found) throw new RuntimeException("setupFog transformer failed"); 
        
        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);

        return classWriter.toByteArray();
    }
    
    public static void setBiomeFogDistance(Entity entity, int distance, float farPlaneDistance)
    {
        World world = entity.worldObj;
        
        int playerX = MathHelper.floor_double(entity.posX);
        int playerZ = MathHelper.floor_double(entity.posZ);
        
        if (playerX == fogX && playerZ == fogZ && fogInit)
        {
            if (distance < 0)
            {
                GL11.glFogf(GL11.GL_FOG_START, 0.0F);
                GL11.glFogf(GL11.GL_FOG_END, farPlaneDistance * 0.8F);
            }
            else
            {
                float fogDistance = Math.min(farPlaneDistance, 256.0F * storedFinalFogCloseness);
                GL11.glFogf(GL11.GL_FOG_START, fogDistance * 0.25F);
                GL11.glFogf(GL11.GL_FOG_END, fogDistance);
            }
            return;
        }
        
        fogInit = true;
        
        int blenddistance = Minecraft.getMinecraft().gameSettings.fancyGraphics ? ForgeDummyContainer.blendRanges[Minecraft.getMinecraft().gameSettings.renderDistance] : 0;
        
        int divider = 0;
        
        float fogCloseness = 0.0F;

        for (int x = -blenddistance; x <= blenddistance; ++x)
        {
            for (int z = -blenddistance; z <= blenddistance; ++z)
            {
                BiomeGenBase biome = world.getBiomeGenForCoords(playerX + x, playerZ + z);

                if (biome instanceof IWCFog)
                {
                    fogCloseness += ((IWCFog)biome).getFogCloseness();
                }
                else
                {
                    fogCloseness += 1.0F;
                }
                
                divider++;
            }
        }
        
        float finalFogCloseness = fogCloseness / divider;
        
        fogX = playerX;
        fogZ = playerZ;
        storedFinalFogCloseness = finalFogCloseness;

        if (distance < 0)
        {
            GL11.glFogf(GL11.GL_FOG_START, 0.0F);
            GL11.glFogf(GL11.GL_FOG_END, farPlaneDistance * 0.8F);
        }
        else
        {
            float fogDistance = Math.min(farPlaneDistance, 256.0F * finalFogCloseness);
            GL11.glFogf(GL11.GL_FOG_START, fogDistance * 0.25F);
            GL11.glFogf(GL11.GL_FOG_END, fogDistance);
        }
    }
}
