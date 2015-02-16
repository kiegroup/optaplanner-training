function toggleShowSolutionBlock(solutionBlock) {
    solutionBlock.prev().toggleClass('stacked');
    solutionBlock.toggle();
}

function insertShowSolutionButtons() {
    $('.solution').each(function(idx, node) {
        var solutionBlock = $(node);
        var showSolutionButton = $('<a class="show-solution" href="#">Show solution</a>');
        solutionBlock.prev().append(showSolutionButton);
        showSolutionButton.on('click', function(event) {
            event.preventDefault();
            toggleShowSolutionBlock(solutionBlock);
            // Expose cheaters (as long as they don't refresh the page)
            showSolutionButton.text('Show/hide solution again');
        });
        toggleShowSolutionBlock(solutionBlock);
    });
}

$(insertShowSolutionButtons);
