pragma solidity ^0.5.6;

// Sample Contract to test compiler functionality
contract Sample {
    address public lastParticipant;

    function getLastParticipant() public {
        lastParticipant = msg.sender;
    }

}
