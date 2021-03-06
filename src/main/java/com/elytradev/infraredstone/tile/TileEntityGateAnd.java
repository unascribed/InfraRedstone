package com.elytradev.infraredstone.tile;

import java.util.ArrayList;
import java.util.List;

import com.elytradev.infraredstone.InfraRedstone;
import com.elytradev.infraredstone.block.BlockGateAnd;
import com.elytradev.infraredstone.block.ModBlocks;
import com.elytradev.infraredstone.logic.InRedLogic;
import com.elytradev.infraredstone.logic.impl.InfraRedstoneHandler;

import com.elytradev.infraredstone.util.EnumInactiveSelection;
import com.google.common.base.Predicates;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.capabilities.Capability;

public class TileEntityGateAnd extends TileEntityIRComponent implements ITickable {
    private InfraRedstoneHandler signal = new InfraRedstoneHandler();
    private int valLeft;
    private int valBack;
    private int valRight;
    public boolean inverted;
    public EnumInactiveSelection inactive = EnumInactiveSelection.NONE;

    //Transient data to throttle sync down here
    boolean lastActive = false;
    int lastValLeft = 0;
    int lastValBack = 0;
    int lastValRight = 0;
    boolean lastInvert = false;
    EnumInactiveSelection lastInactive = EnumInactiveSelection.NONE;

    public void update() {
        if (world.isRemote || !hasWorld()) return;

        IBlockState state = world.getBlockState(this.getPos());

        if (InRedLogic.isIRTick()) {
            //IR tick means we're searching for a next value
            if (state.getBlock() instanceof BlockGateAnd) {
                EnumFacing left = state.getValue(BlockGateAnd.FACING).rotateYCCW();
                EnumFacing right = state.getValue(BlockGateAnd.FACING).rotateY();
                EnumFacing back = state.getValue(BlockGateAnd.FACING).getOpposite();
                int sigLeft = InRedLogic.findIRValue(world, pos, left);
                int sigRight = InRedLogic.findIRValue(world, pos, right);
                int sigBack = InRedLogic.findIRValue(world, pos, back);
                List<Integer> signals = new ArrayList<>();
                
                switch (inactive) {
                case LEFT:
                    signals.add(sigBack);
                    signals.add(sigRight);
                    break;
                case BACK:
                    signals.add(sigLeft);
                    signals.add(sigRight);
                    break;
                case RIGHT:
                    signals.add(sigLeft);
                    signals.add(sigBack);
                    break;
                case NONE:
                    signals.add(sigLeft);
                    signals.add(sigBack);
                    signals.add(sigRight);
                }
                
                int result = 0b11_1111; //63
                for(int signal : signals) {
                    result &= signal;
                }
                if (inverted) result = (~result) & 0b11_1111;
                
                signal.setNextSignalValue(result);
                markDirty();
            }

        } else {
            //Not an IR tick, so this is a "copy" tick. Adopt the previous tick's "next" value.
            signal.setSignalValue(signal.getNextSignalValue());
            markDirty();
        }
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        if (capability==InfraRedstone.CAPABILITY_IR) {
            if (world==null) return true;
            if (facing==null) return true;
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock()==ModBlocks.GATE_AND) {
                EnumFacing gateAndFront = state.getValue(BlockGateAnd.FACING);
                if (gateAndFront==facing) {
                    return true;
                } else if (gateAndFront==facing.rotateYCCW()) {
                    return true;
                } else if (gateAndFront==facing.getOpposite()) {
                    return true;
                } else if (gateAndFront==facing.rotateY()) {
                    return true;
                } else {
                    return false;
                }
            }

            return false;
        }

        return super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability==InfraRedstone.CAPABILITY_IR) {
            if (world==null) return (T)InfraRedstoneHandler.ALWAYS_OFF;
            if (facing==null) return (T) signal;

            IBlockState state = world.getBlockState(pos);
            if (state.getBlock()==ModBlocks.GATE_AND) {
                EnumFacing gateAndFront = state.getValue(BlockGateAnd.FACING);
                if (gateAndFront==facing) {
                    return (T) signal;
                } else if (gateAndFront==facing.rotateYCCW()) {
                    return (T) InfraRedstoneHandler.ALWAYS_OFF;
                } else if (gateAndFront==facing.getOpposite()) {
                    return (T) InfraRedstoneHandler.ALWAYS_OFF;
                } else if (gateAndFront==facing.rotateY()) {
                    return (T)InfraRedstoneHandler.ALWAYS_OFF;
                } else {
                    return null;
                }
            }
            return (T)InfraRedstoneHandler.ALWAYS_OFF; //We can't tell what our front face is, so supply a dummy that's always-off.
        }

        return super.getCapability(capability, facing);
    }

    public void toggleInvert() {
        if (inverted) {
            inverted = false;
            world.playSound(null, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 0.3f, 0.5f);
        } else {
            inverted = true;
            world.playSound(null, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 0.3f, 0.55f);
        }
    }

    public void toggleInactive(EnumInactiveSelection newInactive) {
        if (inactive == newInactive) {
            inactive = EnumInactiveSelection.NONE;
        } else {
            inactive = newInactive;
        }
        world.playSound(null, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 0.3f, 0.45f);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagCompound tag = super.writeToNBT(compound);
        tag.setTag("Signal", InfraRedstone.CAPABILITY_IR.writeNBT(signal, null));
        //please forgive me, falk. We'll work on moving these out soon.
        tag.setInteger("Left", valLeft);
        tag.setInteger("Back", valBack);
        tag.setInteger("Right", valRight);
        tag.setBoolean("Inverted", inverted);
        tag.setString("Inactive", inactive.getName());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("Signal")) InfraRedstone.CAPABILITY_IR.readNBT(signal, null, compound.getTag("Signal"));
        valLeft = compound.getInteger("Left");
        valBack = compound.getInteger("Back");
        valRight = compound.getInteger("Right");
        inverted = compound.getBoolean("Inverted");
        inactive = EnumInactiveSelection.forName(compound.getString("Inactive"));
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        readFromNBT(tag);
        IBlockState state = world.getBlockState(pos);
        getWorld().markAndNotifyBlock(pos, world.getChunkFromBlockCoords(pos), state, state, 1 | 2 | 16);
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        handleUpdateTag(pkt.getNbtCompound());
    }

    @Override
    public void markDirty() {
        super.markDirty();
        // please excuse the black magic
        if (!hasWorld() || getWorld().isRemote) return;

        if (
                   valLeft!=lastValLeft
                || valBack!=lastValBack
                || valRight!=lastValRight
                || isActive()!=lastActive
                || inverted!=lastInvert
                || inactive!=lastInactive) { //Throttle updates - only send when something important changes

            WorldServer ws = (WorldServer)getWorld();
            Chunk c = getWorld().getChunkFromBlockCoords(getPos());
            SPacketUpdateTileEntity packet = new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
            for (EntityPlayerMP player : getWorld().getPlayers(EntityPlayerMP.class, Predicates.alwaysTrue())) {
                if (ws.getPlayerChunkMap().isPlayerWatchingChunk(player, c.x, c.z)) {
                    player.connection.sendPacket(packet);
                }
            }

            lastValLeft = valLeft;
            lastValBack = valBack;
            lastValRight = valRight;
            lastActive = isActive();
            lastInvert = inverted;
            lastInactive = inactive;

            IBlockState state = world.getBlockState(pos);
            ws.markAndNotifyBlock(pos, c, state, state, 1 | 16);
        }
    }

    public boolean isActive() {
        return signal.getSignalValue()!=0;
    }
    public boolean isLeftActive() {
        return valLeft!=0;
    }
    public boolean isBackActive() {
        return valBack!=0;
    }
    public boolean isRightActive() {
        return valRight!=0;
    }
}
