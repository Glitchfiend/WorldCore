package worldcore.asm;

import static org.objectweb.asm.Opcodes.*;

import java.util.Iterator;
import java.util.ListIterator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
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
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import worldcore.interfaces.IWCFog;
import worldcore.interfaces.IWCLighting;

public class WCDimensionLight implements IClassTransformer
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

            if (methodNode.name.equals("updateLightmap") || methodNode.name.equals("h"))
            {
                for (iterator = methodNode.instructions.iterator(); iterator.hasNext();) 
                {
                    AbstractInsnNode insnNode = (AbstractInsnNode)iterator.next();

                    if (insnNode instanceof VarInsnNode)
                    {
                        VarInsnNode varInsnNode = (VarInsnNode)insnNode;

                        if (varInsnNode.getOpcode() == ALOAD && varInsnNode.var == 2)
                        {
                            if (varInsnNode.getNext() instanceof FieldInsnNode)
                            {
                                FieldInsnNode fieldInsnNode = (FieldInsnNode)varInsnNode.getNext();

                                if (fieldInsnNode.getOpcode() == GETFIELD && (fieldInsnNode.name.equals("provider") || fieldInsnNode.name.equals("t")))
                                {
                                    if (fieldInsnNode.getNext() instanceof FieldInsnNode)
                                    {
                                        FieldInsnNode fieldInsnNode2 = (FieldInsnNode)fieldInsnNode.getNext();
                                        
                                        if (fieldInsnNode2.getOpcode() == GETFIELD && (fieldInsnNode2.name.equals("dimensionId") || fieldInsnNode2.name.equals("i")))
                                        {         
                                            InsnList toInject = new InsnList();

                                            toInject.add(new VarInsnNode(ALOAD, 2));
                                            toInject.add(new VarInsnNode(FLOAD, 6));
                                            toInject.add(new VarInsnNode(FLOAD, 9));
                                            toInject.add(new VarInsnNode(FLOAD, 10));
                                            toInject.add(new VarInsnNode(FLOAD, 11));
                                            toInject.add(new VarInsnNode(FLOAD, 12));
                                            toInject.add(new VarInsnNode(FLOAD, 13));
                                            if (obfuscated)
                                                toInject.add(new MethodInsnNode(INVOKESTATIC, "worldcore/asm/WCDimensionLight", "getAmbientLightingValues", "(Lbdd;FFFFFF)[Ljava/lang/Object;"));
                                            else
                                                toInject.add(new MethodInsnNode(INVOKESTATIC, "worldcore/asm/WCDimensionLight", "getAmbientLightingValues", "(Lnet/minecraft/client/multiplayer/WorldClient;FFFFFF)[Ljava/lang/Object;"));                 
                                            toInject.add(new VarInsnNode(ASTORE, 15));
                                            
                                            toInject.add(new VarInsnNode(ALOAD, 15));
                                            toInject.add(new InsnNode(ICONST_0));
                                            toInject.add(new InsnNode(AALOAD));
                                            toInject.add(new TypeInsnNode(CHECKCAST, "java/lang/Float"));
                                            toInject.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F"));
                                            toInject.add(new VarInsnNode(FSTORE, 11));
                                            
                                            toInject.add(new VarInsnNode(ALOAD, 15));
                                            toInject.add(new InsnNode(ICONST_1));
                                            toInject.add(new InsnNode(AALOAD));
                                            toInject.add(new TypeInsnNode(CHECKCAST, "java/lang/Float"));
                                            toInject.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F"));
                                            toInject.add(new VarInsnNode(FSTORE, 12));
                                            
                                            toInject.add(new VarInsnNode(ALOAD, 15));
                                            toInject.add(new InsnNode(ICONST_2));
                                            toInject.add(new InsnNode(AALOAD));
                                            toInject.add(new TypeInsnNode(CHECKCAST, "java/lang/Float"));
                                            toInject.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F"));
                                            toInject.add(new VarInsnNode(FSTORE, 13));

                                            methodNode.instructions.insert(insnNode, toInject);

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

            if (found)
                break;
        }

        if (!found) throw new RuntimeException("updateLightmap transformer failed"); 

        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(classWriter);

        return classWriter.toByteArray();
    }

    public static Object[] getAmbientLightingValues(WorldClient worldclient, float f3, float f6, float f7, float f8, float f9, float f10)
    {
        if (worldclient.provider instanceof IWCLighting)
        {
            IWCLighting provider = ((IWCLighting)worldclient.provider);
            
            if (provider.isLightingDisabled())
            {
                Float[] lightingMultipliers = provider.getLightingMultipliers(worldclient);
                
                float multiplier1 = lightingMultipliers[0];
                float multiplier2 = lightingMultipliers[1];
                float multiplier3 = lightingMultipliers[2];
                
                return new Object[] { multiplier1 + f3 * 0.75F, multiplier2 + f6 * 0.75F, multiplier3 + f7 * 0.75F };
            }
        }
        
        return new Object[] { f8, f9, f10 };
    }
}
